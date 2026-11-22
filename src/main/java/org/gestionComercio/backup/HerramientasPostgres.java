package org.gestionComercio.backup;

import java.util.List;
import java.util.Map;

/**
 * Ejecuta las herramientas de línea de comandos de PostgreSQL (pg_dump,
 * pg_restore). Separado del servicio para poder probarlo contra un PostgreSQL
 * en Docker, que trae sus propias herramientas.
 *
 * <p>Convenciones de los argumentos que reciben las implementaciones: el
 * archivo que genera pg_dump va en {@code --file=<ruta>} y el archivo que lee
 * pg_restore va como último argumento.</p>
 */
public interface HerramientasPostgres {

    String PG_DUMP = "pg_dump";
    String PG_RESTORE = "pg_restore";

    /**
     * Ejecuta la herramienta y espera a que termine.
     *
     * @param herramienta {@link #PG_DUMP} o {@link #PG_RESTORE}
     * @param argumentos  argumentos de la línea de comandos
     * @param entorno     variables de entorno adicionales (conexión)
     */
    ResultadoComando ejecutar(String herramienta, List<String> argumentos, Map<String, String> entorno);
}
