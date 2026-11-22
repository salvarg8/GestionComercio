package org.gestionComercio.support;

import org.gestionComercio.backup.HerramientasPostgres;
import org.gestionComercio.backup.ResultadoComando;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ejecuta pg_dump y pg_restore dentro del contenedor de PostgreSQL de los tests,
 * que trae las herramientas de la misma versión. Copia los archivos entre la PC
 * y el contenedor según las convenciones de {@link HerramientasPostgres}
 * ({@code --file=} para la salida de pg_dump, último argumento para la entrada
 * de pg_restore) y se conecta a la base desde adentro del contenedor.
 */
public class HerramientasEnContenedor implements HerramientasPostgres {

    private final PostgreSQLContainer<?> contenedor;

    public HerramientasEnContenedor(PostgreSQLContainer<?> contenedor) {
        this.contenedor = contenedor;
    }

    @Override
    public ResultadoComando ejecutar(String herramienta, List<String> argumentos, Map<String, String> entorno) {

        List<String> comando = new ArrayList<>(List.of(
                "env",
                "PGHOST=localhost",
                "PGPORT=" + PostgreSQLContainer.POSTGRESQL_PORT,
                "PGUSER=" + entorno.get("PGUSER"),
                "PGPASSWORD=" + entorno.get("PGPASSWORD"),
                herramienta));

        Path salidaLocal = null;
        String salidaContenedor = null;

        for (int i = 0; i < argumentos.size(); i++) {

            String argumento = argumentos.get(i);
            boolean ultimo = i == argumentos.size() - 1;

            if (argumento.startsWith("--file=")) {
                salidaLocal = Path.of(argumento.substring("--file=".length()));
                salidaContenedor = rutaTemporal();
                comando.add("--file=" + salidaContenedor);
            } else if (ultimo && !argumento.startsWith("-") && Files.isRegularFile(Path.of(argumento))) {
                String entrada = rutaTemporal();
                contenedor.copyFileToContainer(MountableFile.forHostPath(Path.of(argumento)), entrada);
                comando.add(entrada);
            } else {
                comando.add(argumento);
            }
        }

        try {
            ExecResult resultado = contenedor.execInContainer(comando.toArray(String[]::new));

            if (salidaLocal != null && resultado.getExitCode() == 0) {
                contenedor.copyFileFromContainer(salidaContenedor, salidaLocal.toString());
            }

            return new ResultadoComando(resultado.getExitCode(), resultado.getStdout() + resultado.getStderr());

        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static String rutaTemporal() {
        return "/tmp/" + UUID.randomUUID() + ".backup";
    }
}
