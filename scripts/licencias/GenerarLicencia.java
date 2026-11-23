import org.gestionComercio.licencia.ClaveLicencia;
import org.gestionComercio.licencia.IdentificadorEquipo;
import org.gestionComercio.licencia.Licencia;
import org.gestionComercio.licencia.ModuloLicencia;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Herramienta interna para emitir licencias de GestionComercio. No forma parte
 * de la aplicación ni del instalador. Usa las clases de target/classes, así que
 * antes hay que compilar (mvn compile).
 *
 * <pre>
 * java -cp target/classes scripts/licencias/GenerarLicencia.java claves &lt;carpeta&gt;
 * java -cp target/classes scripts/licencias/GenerarLicencia.java emitir --clave-privada &lt;archivo&gt;
 *      --cliente "Razón social" --cuit 20123456786 --equipos AAAA-BBBB-CCCC-DDDD,EEEE-FFFF-0000-1111
 *      [--vence 2027-12-31] [--modulos FACTURACION_ELECTRONICA] [--salida licencia.lic]
 * java -cp target/classes scripts/licencias/GenerarLicencia.java ver &lt;archivo.lic&gt;
 * </pre>
 */
public class GenerarLicencia {

    public static void main(String[] args) throws Exception {

        if (args.length == 0) {
            uso();
            return;
        }

        switch (args[0]) {
            case "claves" -> claves(args);
            case "emitir" -> emitir(opciones(args));
            case "ver" -> ver(args);
            default -> uso();
        }
    }

    /** Genera el par de claves. La privada no se versiona ni se distribuye. */
    private static void claves(String[] args) throws Exception {

        if (args.length < 2) {
            uso();
            return;
        }

        Path carpeta = Path.of(args[1]);
        Path privada = carpeta.resolve("licencias-privada.key");

        if (Files.exists(privada)) {
            throw new IllegalStateException("Ya existe " + privada + ". No se sobrescribe: las licencias "
                    + "emitidas con esa clave dejarían de poder renovarse.");
        }

        KeyPair par = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publica = Base64.getEncoder().encodeToString(par.getPublic().getEncoded());

        Files.createDirectories(carpeta);
        Files.writeString(privada, Base64.getEncoder().encodeToString(par.getPrivate().getEncoded()) + "\n");
        Files.writeString(carpeta.resolve("licencias-publica.txt"), publica + "\n");

        System.out.println("Clave privada: " + privada.toAbsolutePath());
        System.out.println("  Guárdela en un lugar seguro y haga una copia. Si se pierde, no se pueden emitir");
        System.out.println("  más licencias para las instalaciones existentes.");
        System.out.println();
        System.out.println("Clave pública (va en VerificadorLicencia.CLAVE_PUBLICA):");
        System.out.println(publica);
    }

    private static void emitir(Map<String, String> opciones) throws Exception {

        String clavePrivada = Files.readString(Path.of(requerida(opciones, "clave-privada")));
        String cuit = requerida(opciones, "cuit").replaceAll("\\D", "");

        if (cuit.length() != 11) {
            throw new IllegalArgumentException("El CUIT debe tener 11 dígitos: " + cuit);
        }

        List<String> equipos = Arrays.stream(requerida(opciones, "equipos").split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();

        String vence = opciones.get("vence");

        Licencia licencia = new Licencia(
                UUID.randomUUID().toString(),
                requerida(opciones, "cliente"),
                cuit,
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS),
                vence == null || vence.isBlank() ? null : LocalDate.parse(vence),
                modulos(opciones.getOrDefault("modulos", "")),
                equipos);

        String clave = ClaveLicencia.firmar(licencia, ClaveLicencia.leerClavePrivada(clavePrivada));
        String contenido = ClaveLicencia.archivo(licencia, clave);

        Path salida = Path.of(opciones.getOrDefault("salida",
                "licencia_" + cuit + "_" + LocalDate.now() + ".lic"));
        Files.writeString(salida, contenido, StandardCharsets.UTF_8);

        System.out.println("Licencia emitida: " + salida.toAbsolutePath());
        imprimir(licencia);
    }

    /** Muestra los datos de un archivo .lic y verifica su firma con la clave pública indicada o la de la aplicación. */
    private static void ver(String[] args) throws Exception {

        if (args.length < 2) {
            uso();
            return;
        }

        String clave = Files.readString(Path.of(args[1]));
        PublicKey publica = args.length >= 3
                ? ClaveLicencia.leerClavePublica(Files.readString(Path.of(args[2])))
                : clavePublicaDeLaAplicacion();

        Licencia licencia = ClaveLicencia.verificar(clave, publica);
        System.out.println("Firma válida.");
        imprimir(licencia);
    }

    private static PublicKey clavePublicaDeLaAplicacion() throws Exception {
        var campo = Class.forName("org.gestionComercio.licencia.VerificadorLicencia")
                .getDeclaredField("CLAVE_PUBLICA");
        campo.setAccessible(true);
        return ClaveLicencia.leerClavePublica((String) campo.get(null));
    }

    private static void imprimir(Licencia licencia) {
        System.out.println("  Id:          " + licencia.id());
        System.out.println("  Cliente:     " + licencia.cliente());
        System.out.println("  CUIT:        " + licencia.cuit());
        System.out.println("  Emitida:     " + licencia.emitida());
        System.out.println("  Vencimiento: " + (licencia.perpetua() ? "sin vencimiento (pago único)"
                : licencia.vencimiento()));
        System.out.println("  Módulos:     " + (licencia.modulos().isEmpty() ? "(ninguno)" : licencia.modulos()));
        System.out.println("  Equipos:     " + String.join(", ", licencia.equipos().stream()
                .map(IdentificadorEquipo::formatear).toList()));
    }

    private static Set<ModuloLicencia> modulos(String texto) {
        Set<ModuloLicencia> modulos = EnumSet.noneOf(ModuloLicencia.class);
        for (String nombre : texto.split(",")) {
            if (!nombre.isBlank()) {
                modulos.add(ModuloLicencia.valueOf(nombre.strip().toUpperCase()));
            }
        }
        return modulos;
    }

    private static Map<String, String> opciones(String[] args) {
        Map<String, String> opciones = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Opción inválida: " + args[i]);
            }
            opciones.put(args[i].substring(2), args[++i]);
        }
        return opciones;
    }

    private static String requerida(Map<String, String> opciones, String nombre) {
        String valor = opciones.get(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta --" + nombre);
        }
        return valor;
    }

    private static void uso() {
        System.out.println("""
                Uso:
                  claves <carpeta>
                      Genera el par de claves (una sola vez). La privada no se versiona.
                  emitir --clave-privada <archivo> --cliente "<razón social>" --cuit <cuit>
                         --equipos <código>[,<código>...] [--vence AAAA-MM-DD]
                         [--modulos FACTURACION_ELECTRONICA] [--salida <archivo.lic>]
                      Sin --vence la licencia no vence (pago único).
                  ver <archivo.lic> [<clave-publica.txt>]
                      Verifica la firma y muestra los datos.""");
    }

}
