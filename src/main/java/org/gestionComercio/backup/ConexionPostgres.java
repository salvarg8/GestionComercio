package org.gestionComercio.backup;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Datos de conexión que necesitan pg_dump y pg_restore, tomados de la URL JDBC
 * de la aplicación. Se pasan por variables de entorno (PGHOST, PGPASSWORD, etc.)
 * para que la contraseña no quede visible en la línea de comandos.
 */
public record ConexionPostgres(String host, int puerto, String base, String usuario, String password) {

    private static final int PUERTO_POR_DEFECTO = 5432;

    /**
     * Interpreta una URL {@code jdbc:postgresql://host[:puerto]/base[?parametros]}.
     * Los parámetros JDBC se ignoran: no los entienden las herramientas de PostgreSQL.
     */
    public static ConexionPostgres desdeJdbc(String url, String usuario, String password) {

        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException(
                    "URL de base de datos no soportada para copias de seguridad: " + url);
        }

        try {
            URI uri = new URI(url.substring("jdbc:".length()));

            String base = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");

            if (uri.getHost() == null || base.isBlank()) {
                throw new IllegalArgumentException(
                        "La URL de base de datos debe indicar servidor y base: " + url);
            }

            int puerto = uri.getPort() > 0 ? uri.getPort() : PUERTO_POR_DEFECTO;

            return new ConexionPostgres(uri.getHost(), puerto, base, usuario, password);

        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("URL de base de datos inválida: " + url, ex);
        }
    }

    /** Variables de entorno para pg_dump y pg_restore. */
    public Map<String, String> entorno() {

        Map<String, String> entorno = new HashMap<>();
        entorno.put("PGHOST", host);
        entorno.put("PGPORT", String.valueOf(puerto));
        entorno.put("PGUSER", usuario == null ? "" : usuario);
        entorno.put("PGPASSWORD", password == null ? "" : password);
        return entorno;
    }

    @Override
    public String toString() {
        // Sin la contraseña, por si termina en un log.
        return usuario + "@" + host + ":" + puerto + "/" + base;
    }
}
