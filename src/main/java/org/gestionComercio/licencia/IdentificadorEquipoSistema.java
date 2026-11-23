package org.gestionComercio.licencia;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Código de equipo a partir del identificador que el sistema operativo le
 * asigna a la instalación: en Windows el MachineGuid del registro, en Linux
 * /etc/machine-id y en macOS el IOPlatformUUID. Se publica sólo un hash, no el
 * identificador original.
 *
 * <p>El código cambia si se reinstala el sistema operativo; en ese caso se
 * pide al proveedor una licencia con el código nuevo (igual que al cambiar de PC).
 */
@Slf4j
@Component
public class IdentificadorEquipoSistema implements IdentificadorEquipo {

    private static final Pattern MACHINE_GUID = Pattern.compile("MachineGuid\\s+REG_SZ\\s+(\\S+)");

    private static final Pattern PLATFORM_UUID = Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\"");

    /** Bytes del hash que forman el código (8 bytes: 16 caracteres hexadecimales). */
    private static final int LARGO_CODIGO = 8;

    private String codigo;

    @Override
    public synchronized String obtener() {
        if (codigo == null) {
            codigo = calcular(identificadorDelSistema());
        }
        return codigo;
    }

    static String calcular(String identificador) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(("GestionComercio|" + identificador.strip().toLowerCase(Locale.ROOT))
                            .getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().withUpperCase().formatHex(hash, 0, LARGO_CODIGO);
            return IdentificadorEquipo.formatear(hex);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String identificadorDelSistema() {

        String so = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        Optional<String> identificador;
        if (so.contains("win")) {
            identificador = ejecutar(MACHINE_GUID, "reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                    "/v", "MachineGuid", "/reg:64");
        } else if (so.contains("mac")) {
            identificador = ejecutar(PLATFORM_UUID, "ioreg", "-rd1", "-c", "IOPlatformExpertDevice");
        } else {
            identificador = leerArchivo(Path.of("/etc/machine-id"))
                    .or(() -> leerArchivo(Path.of("/var/lib/dbus/machine-id")));
        }

        return identificador.orElseGet(() -> {
            String nombre = nombreDelEquipo();
            log.warn("No se pudo leer el identificador del sistema operativo; se usa el nombre del equipo ({}).",
                    nombre);
            return "host:" + nombre;
        });
    }

    private static Optional<String> ejecutar(Pattern patron, String... comando) {
        try {
            Process proceso = new ProcessBuilder(List.of(comando)).redirectErrorStream(true).start();
            String salida = new String(proceso.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            if (!proceso.waitFor(10, TimeUnit.SECONDS)) {
                proceso.destroyForcibly();
                return Optional.empty();
            }

            Matcher m = patron.matcher(salida);
            return m.find() ? Optional.of(m.group(1)) : Optional.empty();
        } catch (IOException e) {
            log.warn("No se pudo ejecutar {}: {}", comando[0], e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static Optional<String> leerArchivo(Path archivo) {
        try {
            String contenido = Files.readString(archivo).strip();
            return contenido.isEmpty() ? Optional.empty() : Optional.of(contenido);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String nombreDelEquipo() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return System.getenv().getOrDefault("COMPUTERNAME", "desconocido");
        }
    }

}
