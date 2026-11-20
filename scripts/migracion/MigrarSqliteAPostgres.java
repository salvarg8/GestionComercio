import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copia los datos de la base SQLite de desarrollo a PostgreSQL (COM40).
 *
 * <p>Es un programa de un solo archivo que no forma parte de la aplicación:
 * se ejecuta una vez con {@code java} y los drivers en el classpath (ver el
 * README). Requisitos:</p>
 * <ul>
 *   <li>PostgreSQL levantado y la aplicación arrancada al menos una vez contra
 *       él, para que Flyway cree las tablas.</li>
 *   <li>Un usuario con permiso para {@code session_replication_role} (el del
 *       docker-compose.yml lo tiene).</li>
 * </ul>
 *
 * <p>Vacía las tablas de PostgreSQL (incluidos los datos iniciales que creó la
 * aplicación, pero no el historial de Flyway), copia cada tabla que exista en ambas bases convirtiendo los
 * valores al tipo de la columna de destino y ajusta las secuencias de los id.
 * Todo corre en una sola transacción: si algo falla, PostgreSQL queda como
 * estaba.</p>
 *
 * <p>Uso: {@code java -cp <sqlite-jdbc.jar>;<postgresql.jar> MigrarSqliteAPostgres.java
 * <archivo.db> [url] [usuario] [contraseña]}</p>
 */
public class MigrarSqliteAPostgres {

    private static final DateTimeFormatter FECHA_HORA_SQLITE = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd[ ]['T']HH:mm[:ss]")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .toFormatter();

    public static void main(String[] args) throws Exception {

        if (args.length < 1) {
            System.err.println("Uso: MigrarSqliteAPostgres <archivo.db> [url] [usuario] [contraseña]");
            System.exit(1);
        }

        String archivo = args[0];
        String url = args.length > 1 ? args[1] : "jdbc:postgresql://localhost:5432/gestion_comercio";
        String usuario = args.length > 2 ? args[2] : "gestion";
        String password = args.length > 3 ? args[3] : "gestion";

        try (Connection origen = DriverManager.getConnection("jdbc:sqlite:" + archivo);
             Connection destino = DriverManager.getConnection(url, usuario, password)) {

            destino.setAutoCommit(false);

            try {
                migrar(origen, destino);
                destino.commit();
                System.out.println("Migración completa.");
            } catch (Exception e) {
                destino.rollback();
                System.err.println("Falló la migración; no se modificó PostgreSQL.");
                throw e;
            }
        }
    }

    private static void migrar(Connection origen, Connection destino) throws SQLException {

        Set<String> tablasOrigen = tablasSqlite(origen);
        Map<String, Map<String, String>> tablasDestino = tablasPostgres(destino);

        if (tablasDestino.isEmpty()) {
            throw new IllegalStateException("PostgreSQL no tiene tablas: arranque la aplicación una vez "
                    + "contra la base vacía para que se creen y vuelva a ejecutar el script.");
        }

        for (String tabla : tablasOrigen) {
            if (!tablasDestino.containsKey(tabla)) {
                System.out.println("AVISO: la tabla " + tabla + " no existe en PostgreSQL; se omite.");
            }
        }

        try (Statement st = destino.createStatement()) {
            // Sin controles de claves foráneas mientras se copia: el orden de las
            // tablas no importa. Se reactivan al terminar la transacción.
            st.execute("set local session_replication_role = replica");
            st.execute("truncate table " + String.join(", ", tablasDestino.keySet().stream()
                    .map(MigrarSqliteAPostgres::comillas).toList()) + " restart identity cascade");
        }

        for (Map.Entry<String, Map<String, String>> entrada : tablasDestino.entrySet()) {

            String tabla = entrada.getKey();

            if (!tablasOrigen.contains(tabla)) {
                System.out.println("AVISO: la tabla " + tabla + " no existe en SQLite; queda vacía.");
                continue;
            }

            int filas = copiarTabla(origen, destino, tabla, entrada.getValue());
            System.out.printf("%-30s %6d filas%n", tabla, filas);
        }

        ajustarSecuencias(destino, tablasDestino);
    }

    private static int copiarTabla(Connection origen, Connection destino, String tabla,
                                   Map<String, String> tiposDestino) throws SQLException {

        List<String> columnasOrigen = columnasSqlite(origen, tabla);
        List<String> columnas = new ArrayList<>();

        for (String columna : columnasOrigen) {
            if (tiposDestino.containsKey(columna)) {
                columnas.add(columna);
            } else {
                System.out.println("AVISO: " + tabla + "." + columna + " no existe en PostgreSQL; se omite.");
            }
        }

        if (columnas.isEmpty()) {
            return 0;
        }

        String lista = String.join(", ", columnas.stream().map(MigrarSqliteAPostgres::comillas).toList());
        String marcadores = String.join(", ", columnas.stream().map(c -> "?").toList());

        int filas = 0;

        try (Statement lectura = origen.createStatement();
             ResultSet rs = lectura.executeQuery("select " + lista + " from " + comillas(tabla));
             PreparedStatement insercion = destino.prepareStatement(
                     "insert into " + comillas(tabla) + " (" + lista + ") values (" + marcadores + ")")) {

            while (rs.next()) {
                for (int i = 0; i < columnas.size(); i++) {
                    String columna = columnas.get(i);
                    Object valor = convertir(rs.getObject(i + 1), tiposDestino.get(columna), tabla + "." + columna);
                    insercion.setObject(i + 1, valor);
                }
                insercion.addBatch();

                if (++filas % 500 == 0) {
                    insercion.executeBatch();
                }
            }

            insercion.executeBatch();
        }

        return filas;
    }

    /**
     * Convierte un valor leído de SQLite (que guarda casi todo como entero,
     * real o texto) al tipo de la columna de PostgreSQL.
     */
    static Object convertir(Object valor, String tipo, String columna) {

        if (valor == null) {
            return null;
        }

        try {
            return switch (tipo) {
                case "boolean" -> valor instanceof Number n
                        ? n.intValue() != 0
                        : Boolean.parseBoolean(valor.toString()) || "1".equals(valor.toString());
                case "smallint", "integer" -> ((Number) numero(valor)).intValue();
                case "bigint" -> ((Number) numero(valor)).longValue();
                case "numeric", "double precision", "real" -> new BigDecimal(valor.toString());
                case "timestamp without time zone" -> Timestamp.valueOf(fechaHora(valor));
                case "date" -> java.sql.Date.valueOf(fecha(valor));
                case "bytea" -> valor;
                default -> valor.toString();
            };
        } catch (RuntimeException e) {
            throw new IllegalStateException("No se pudo convertir " + columna + " = '" + valor
                    + "' al tipo " + tipo, e);
        }
    }

    private static Object numero(Object valor) {
        return valor instanceof Number ? valor : new BigDecimal(valor.toString().trim());
    }

    /** El driver de SQLite guarda las fechas como milisegundos o como texto. */
    private static LocalDateTime fechaHora(Object valor) {
        if (valor instanceof Number n) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(n.longValue()), ZoneId.systemDefault());
        }
        String texto = valor.toString().trim();
        if (texto.matches("-?\\d+")) {
            return fechaHora(Long.parseLong(texto));
        }
        if (texto.length() == 10) {
            return LocalDate.parse(texto).atStartOfDay();
        }
        return LocalDateTime.parse(texto, FECHA_HORA_SQLITE);
    }

    private static LocalDate fecha(Object valor) {
        String texto = valor.toString().trim();
        if (valor instanceof Number || texto.matches("-?\\d+")) {
            return fechaHora(valor).toLocalDate();
        }
        return LocalDate.parse(texto.substring(0, 10));
    }

    private static void ajustarSecuencias(Connection destino, Map<String, Map<String, String>> tablas)
            throws SQLException {

        for (Map.Entry<String, Map<String, String>> entrada : tablas.entrySet()) {

            String tabla = entrada.getKey();

            // Las tablas intermedias (por ejemplo, roles_permisos) no tienen id.
            // Hay que filtrarlas antes: un error dentro de la transacción la anula.
            if (!entrada.getValue().containsKey("id")) {
                continue;
            }

            String secuencia;

            try (PreparedStatement ps = destino.prepareStatement("select pg_get_serial_sequence(?, 'id')")) {
                ps.setString(1, comillas(tabla));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    secuencia = rs.getString(1);
                }
            }

            if (secuencia == null) {
                continue;
            }

            try (Statement st = destino.createStatement()) {
                st.execute("select setval('" + secuencia + "', coalesce(max(id), 0) + 1, false) from "
                        + comillas(tabla));
            }
        }
    }

    private static Set<String> tablasSqlite(Connection origen) throws SQLException {
        Set<String> tablas = new LinkedHashSet<>();
        try (Statement st = origen.createStatement();
             ResultSet rs = st.executeQuery(
                     "select name from sqlite_master where type = 'table' and name not like 'sqlite_%'")) {
            while (rs.next()) {
                tablas.add(rs.getString(1));
            }
        }
        return tablas;
    }

    private static List<String> columnasSqlite(Connection origen, String tabla) throws SQLException {
        List<String> columnas = new ArrayList<>();
        try (Statement st = origen.createStatement();
             ResultSet rs = st.executeQuery("select * from " + comillas(tabla) + " limit 0")) {
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                columnas.add(rs.getMetaData().getColumnName(i));
            }
        }
        return columnas;
    }

    /**
     * Tabla → (columna → tipo) del esquema public de PostgreSQL, sin el historial
     * de Flyway: si se vaciara, la aplicación no volvería a arrancar.
     */
    private static Map<String, Map<String, String>> tablasPostgres(Connection destino) throws SQLException {
        Map<String, Map<String, String>> tablas = new LinkedHashMap<>();
        try (Statement st = destino.createStatement();
             ResultSet rs = st.executeQuery("""
                     select c.table_name, c.column_name, c.data_type
                     from information_schema.columns c
                     join information_schema.tables t
                       on t.table_schema = c.table_schema and t.table_name = c.table_name
                     where c.table_schema = 'public' and t.table_type = 'BASE TABLE'
                       and c.table_name <> 'flyway_schema_history'
                     order by c.table_name, c.ordinal_position
                     """)) {
            while (rs.next()) {
                tablas.computeIfAbsent(rs.getString(1), t -> new LinkedHashMap<>())
                        .put(rs.getString(2), rs.getString(3));
            }
        }
        return tablas;
    }

    private static String comillas(String identificador) {
        return "\"" + identificador.replace("\"", "\"\"") + "\"";
    }
}
