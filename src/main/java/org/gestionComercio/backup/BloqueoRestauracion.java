package org.gestionComercio.backup;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Aviso de "restauración en curso" compartido por todas las PC.
 *
 * <p>Se usa un advisory lock de PostgreSQL: lo toma la conexión de la PC que
 * restaura y lo ven todas las demás en {@code pg_locks}. No depende de ninguna
 * tabla (la restauración las reemplaza) y se libera solo si esa PC se cae.</p>
 */
@Component
public class BloqueoRestauracion {

    /** Clave del advisory lock (arbitraria, propia de la aplicación). */
    static final long CLAVE = 350_035L;

    private static final String SQL_EN_CURSO = """
            select exists (
                select 1
                from pg_locks l
                join pg_database d on d.oid = l.database
                where l.locktype = 'advisory'
                  and l.granted
                  and d.datname = current_database()
                  and l.classid::bigint = 0
                  and l.objid::bigint = ?
                  and l.objsubid = 1
                  and l.pid <> pg_backend_pid()
            )""";

    private final JdbcTemplate jdbcTemplate;

    /** true mientras esta misma instancia está restaurando. */
    private volatile boolean restaurandoAqui;

    public BloqueoRestauracion(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    /** Indica si alguna PC (incluida esta) está restaurando la base. */
    public boolean restauracionEnCurso() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(SQL_EN_CURSO, Boolean.class, CLAVE));
    }

    /** Indica si otra PC está restaurando la base. */
    public boolean restauracionEnOtraPc() {
        return !restaurandoAqui && restauracionEnCurso();
    }

    public boolean isRestaurandoAqui() {
        return restaurandoAqui;
    }

    /**
     * Toma el aviso en la conexión indicada, que debe quedar abierta durante toda
     * la restauración.
     *
     * @return false si otra PC ya está restaurando
     */
    public boolean tomar(Connection conexion) throws SQLException {

        try (PreparedStatement ps = conexion.prepareStatement("select pg_try_advisory_lock(?)")) {

            ps.setLong(1, CLAVE);

            try (ResultSet rs = ps.executeQuery()) {
                boolean tomado = rs.next() && rs.getBoolean(1);
                restaurandoAqui = tomado;
                return tomado;
            }
        }
    }

    public void liberar(Connection conexion) {

        restaurandoAqui = false;

        try (PreparedStatement ps = conexion.prepareStatement("select pg_advisory_unlock(?)")) {
            ps.setLong(1, CLAVE);
            ps.execute();
        } catch (SQLException ex) {
            // Al cerrarse la conexión, PostgreSQL lo libera igual.
        }
    }

    /**
     * Corta las demás conexiones a la base (de otras PC y del pool de esta),
     * salvo la que tiene el aviso. Sólo las del mismo usuario de base de datos:
     * son las de la aplicación y las únicas que se pueden cortar sin ser superusuario.
     *
     * @return cantidad de conexiones cortadas
     */
    public int desconectarOtrasSesiones(Connection conexion) throws SQLException {

        String sql = """
                select count(*) filter (where pg_terminate_backend(pid))
                from pg_stat_activity
                where datname = current_database()
                  and usename = current_user
                  and backend_type = 'client backend'
                  and pid <> pg_backend_pid()""";

        try (PreparedStatement ps = conexion.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
