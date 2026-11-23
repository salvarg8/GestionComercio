package org.gestionComercio.licencia;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Formato de la clave de licencia: {@code GC1.<datos>.<firma>}, con los datos
 * (texto {@code campo=valor}, uno por línea, en UTF-8) y la firma Ed25519 de
 * esos bytes en Base64 URL. Cualquier cambio en los datos invalida la firma.
 *
 * <p>Los espacios y saltos de línea se ignoran (la clave puede llegar cortada
 * en un correo), igual que las líneas que empiezan con {@code #}: los archivos
 * {@code .lic} llevan un encabezado legible.
 *
 * <p>La aplicación sólo tiene la clave pública. La privada, con la que se
 * firma, la usa únicamente la herramienta interna (scripts/licencias).
 */
public final class ClaveLicencia {

    public static final String PREFIJO = "GC1";

    private static final String ALGORITMO = "Ed25519";

    private static final int VERSION_FORMATO = 1;

    private ClaveLicencia() {
    }

    // ------------------------------------------------------------------
    // Firma (herramienta interna y tests)
    // ------------------------------------------------------------------

    /** @return la clave de licencia firmada con la clave privada del proveedor */
    public static String firmar(Licencia licencia, PrivateKey clavePrivada) {

        byte[] datos = serializar(licencia).getBytes(StandardCharsets.UTF_8);

        try {
            Signature firma = Signature.getInstance(ALGORITMO);
            firma.initSign(clavePrivada);
            firma.update(datos);
            return PREFIJO + "." + base64(datos) + "." + base64(firma.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar la licencia: " + e.getMessage(), e);
        }
    }

    /** Contenido de un archivo {@code .lic}: un encabezado legible y la clave. */
    public static String archivo(Licencia licencia, String clave) {
        return "# Licencia de GestionComercio\n"
                + "# Cliente: " + licencia.cliente() + " - CUIT " + licencia.cuit() + "\n"
                + "# Vencimiento: " + (licencia.perpetua() ? "sin vencimiento" : licencia.vencimiento()) + "\n"
                + "# Equipos: " + licencia.equipos().size() + "\n"
                + clave + "\n";
    }

    // ------------------------------------------------------------------
    // Verificación
    // ------------------------------------------------------------------

    /**
     * Verifica la firma y lee los datos.
     *
     * @throws LicenciaInvalidaException si la clave está mal formada o la firma no corresponde
     */
    public static Licencia verificar(String clave, PublicKey clavePublica) {

        String normalizada = normalizar(clave);
        String[] partes = normalizada.split("\\.");

        if (partes.length != 3 || !PREFIJO.equals(partes[0])) {
            throw new LicenciaInvalidaException("El texto ingresado no es una clave de licencia.");
        }

        byte[] datos;
        byte[] firma;
        try {
            datos = Base64.getUrlDecoder().decode(partes[1]);
            firma = Base64.getUrlDecoder().decode(partes[2]);
        } catch (IllegalArgumentException e) {
            throw new LicenciaInvalidaException("La clave de licencia está incompleta o dañada.", e);
        }

        if (!firmaValida(datos, firma, clavePublica)) {
            throw new LicenciaInvalidaException(
                    "La clave de licencia fue modificada o no fue emitida por el proveedor del sistema.");
        }

        return deserializar(new String(datos, StandardCharsets.UTF_8));
    }

    /** Quita comentarios, espacios y saltos de línea. */
    public static String normalizar(String clave) {

        if (clave == null) {
            return "";
        }

        return clave.lines()
                .map(String::strip)
                .filter(linea -> !linea.startsWith("#"))
                .collect(Collectors.joining())
                .replaceAll("\\s", "");
    }

    private static boolean firmaValida(byte[] datos, byte[] firma, PublicKey clavePublica) {
        try {
            Signature verificador = Signature.getInstance(ALGORITMO);
            verificador.initVerify(clavePublica);
            verificador.update(datos);
            return verificador.verify(firma);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Claves
    // ------------------------------------------------------------------

    /** @param base64 clave pública en formato X.509, en Base64 */
    public static PublicKey leerClavePublica(String base64) {
        try {
            return KeyFactory.getInstance(ALGORITMO)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Clave pública de licencias inválida: " + e.getMessage(), e);
        }
    }

    /** @param base64 clave privada en formato PKCS#8, en Base64 */
    public static PrivateKey leerClavePrivada(String base64) {
        try {
            return KeyFactory.getInstance(ALGORITMO)
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64.strip())));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Clave privada de licencias inválida: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Datos
    // ------------------------------------------------------------------

    static String serializar(Licencia licencia) {

        String modulos = licencia.modulos().stream()
                .sorted(Comparator.naturalOrder())
                .map(Enum::name)
                .collect(Collectors.joining(","));

        return "formato=" + VERSION_FORMATO + "\n"
                + "id=" + valor(licencia.id()) + "\n"
                + "cliente=" + valor(licencia.cliente()) + "\n"
                + "cuit=" + valor(licencia.cuit()) + "\n"
                + "emitida=" + licencia.emitida() + "\n"
                + "vencimiento=" + (licencia.perpetua() ? "" : licencia.vencimiento()) + "\n"
                + "modulos=" + modulos + "\n"
                + "equipos=" + String.join(",", licencia.equipos()) + "\n";
    }

    static Licencia deserializar(String texto) {

        Map<String, String> campos = new HashMap<>();
        texto.lines().forEach(linea -> {
            int igual = linea.indexOf('=');
            if (igual > 0) {
                campos.put(linea.substring(0, igual), linea.substring(igual + 1));
            }
        });

        if (!String.valueOf(VERSION_FORMATO).equals(campos.get("formato"))) {
            throw new LicenciaInvalidaException(
                    "La licencia es de una versión más nueva del sistema. Actualice la aplicación.");
        }

        try {
            String vencimiento = campos.getOrDefault("vencimiento", "");

            return new Licencia(
                    campos.get("id"),
                    campos.get("cliente"),
                    campos.get("cuit"),
                    LocalDateTime.parse(campos.get("emitida")),
                    vencimiento.isBlank() ? null : LocalDate.parse(vencimiento),
                    modulos(campos.getOrDefault("modulos", "")),
                    lista(campos.getOrDefault("equipos", "")));
        } catch (NullPointerException | DateTimeParseException e) {
            throw new LicenciaInvalidaException("Los datos de la licencia están incompletos.", e);
        }
    }

    /** Los módulos que esta versión no conoce (de una versión más nueva) se ignoran. */
    private static Set<ModuloLicencia> modulos(String texto) {

        Set<ModuloLicencia> modulos = EnumSet.noneOf(ModuloLicencia.class);

        for (String nombre : lista(texto)) {
            Arrays.stream(ModuloLicencia.values())
                    .filter(m -> m.name().equals(nombre))
                    .findFirst()
                    .ifPresent(modulos::add);
        }

        return modulos;
    }

    private static List<String> lista(String texto) {
        return Arrays.stream(texto.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String valor(String texto) {
        if (texto.contains("\n") || texto.contains("\r")) {
            throw new IllegalArgumentException("Los datos de la licencia no pueden tener saltos de línea: " + texto);
        }
        return texto.strip();
    }

    private static String base64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
