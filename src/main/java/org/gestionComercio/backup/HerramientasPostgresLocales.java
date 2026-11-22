package org.gestionComercio.backup;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ejecuta pg_dump y pg_restore instalados en esta PC. Los busca, en orden, en
 * la carpeta configurada ({@code backup.herramientas} / {@code PG_BIN}), en
 * {@code pgsql/bin} (donde las deja el instalador) y en el PATH.
 */
@Component
@RequiredArgsConstructor
public class HerramientasPostgresLocales implements HerramientasPostgres {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().startsWith("windows");

    private final BackupProperties properties;

    @Override
    public ResultadoComando ejecutar(String herramienta, List<String> argumentos, Map<String, String> entorno) {

        List<String> comando = new ArrayList<>();
        comando.add(ubicar(herramienta).toString());
        comando.addAll(argumentos);

        ProcessBuilder builder = new ProcessBuilder(comando).redirectErrorStream(true);
        builder.environment().putAll(entorno);

        try {
            Process proceso = builder.start();

            // La salida se lee entera antes de esperar: si nadie la lee, el proceso se puede trabar.
            String salida = new String(proceso.getInputStream().readAllBytes(), charsetDelSistema());

            return new ResultadoComando(proceso.waitFor(), salida);

        } catch (IOException ex) {
            throw new BusinessException("No se pudo ejecutar " + herramienta + ": " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Se interrumpió la ejecución de " + herramienta + ".", ex);
        }
    }

    Path ubicar(String herramienta) {

        String ejecutable = WINDOWS ? herramienta + ".exe" : herramienta;

        for (Path carpeta : carpetasDeBusqueda()) {
            Path candidato = carpeta.resolve(ejecutable);
            if (Files.isRegularFile(candidato)) {
                return candidato;
            }
        }

        throw new BusinessException("No se encontró " + herramienta + " (herramientas de PostgreSQL). "
                + "Instálelas o indique su carpeta en la variable de entorno PG_BIN "
                + "(por ejemplo, C:\\Program Files\\PostgreSQL\\17\\bin).");
    }

    private List<Path> carpetasDeBusqueda() {

        List<Path> carpetas = new ArrayList<>();

        if (properties.herramientas() != null && !properties.herramientas().isBlank()) {
            carpetas.add(Path.of(properties.herramientas()));
            return carpetas;
        }

        carpetas.add(Path.of("pgsql", "bin"));

        String path = System.getenv("PATH");
        if (path != null) {
            for (String carpeta : path.split(File.pathSeparator)) {
                if (!carpeta.isBlank()) {
                    try {
                        carpetas.add(Path.of(carpeta.trim()));
                    } catch (RuntimeException ex) {
                        // Entrada inválida del PATH: se ignora.
                    }
                }
            }
        }

        return carpetas;
    }

    private static Charset charsetDelSistema() {
        try {
            return Charset.forName(System.getProperty("native.encoding", "UTF-8"));
        } catch (RuntimeException ex) {
            return Charset.defaultCharset();
        }
    }
}
