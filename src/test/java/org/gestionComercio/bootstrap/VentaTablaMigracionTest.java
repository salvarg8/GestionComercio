package org.gestionComercio.bootstrap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.io.File;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba la migración contra una base SQLite real con la tabla ventas del
 * esquema anterior (cliente_id NOT NULL).
 */
class VentaTablaMigracionTest {

    private JdbcTemplate jdbc;

    private SingleConnectionDataSource dataSource;

    private File archivo;

    private VentaTablaMigracion migracion;

    @BeforeEach
    void setUp() {
        archivo = new File("target/venta-migracion-test.db");
        archivo.delete();

        dataSource = new SingleConnectionDataSource("jdbc:sqlite:" + archivo.getPath(), true);
        jdbc = new JdbcTemplate(dataSource);
        migracion = new VentaTablaMigracion(jdbc);
    }

    @AfterEach
    void tearDown() {
        // En Windows el archivo queda bloqueado mientras la conexión esté abierta.
        dataSource.destroy();
        archivo.delete();
    }

    private void crearTablaVieja() {
        jdbc.execute("""
                create table ventas (
                    id integer,
                    estado_registro varchar(255) not null,
                    fecha_creacion timestamp not null,
                    fecha_modificacion timestamp not null,
                    usuario_creacion_id bigint,
                    usuario_modificacion_id bigint,
                    fecha timestamp not null,
                    subtotal numeric(12,2) not null,
                    descuento numeric(12,2) not null,
                    total numeric(12,2) not null,
                    forma_pago varchar(255) not null,
                    cliente_id bigint not null,
                    usuario_id bigint not null,
                    caja_id bigint not null,
                    estado_venta varchar(255) not null,
                    primary key (id)
                )
                """);
    }

    private int clienteNotNull() {
        return jdbc.queryForObject(
                "select \"notnull\" from pragma_table_info('ventas') where name = 'cliente_id'", Integer.class);
    }

    @Test
    @DisplayName("Quita el NOT NULL de cliente_id conservando las ventas existentes")
    void migraTablaVieja() {
        crearTablaVieja();
        jdbc.update("""
                insert into ventas values (7, 'ACTIVO', '2026-01-01', '2026-01-01', null, null,
                    '2026-01-01', 100, 0, 100, 'EFECTIVO', 3, 1, 1, 'PAGADA')
                """);

        migracion.migrarSiCorresponde();

        assertThat(clienteNotNull()).isZero();
        Map<String, Object> venta = jdbc.queryForMap("select id, cliente_id, total from ventas");
        assertThat(venta.get("id")).isEqualTo(7);
        assertThat(((Number) venta.get("cliente_id")).longValue()).isEqualTo(3L);

        jdbc.update("""
                insert into ventas (estado_registro, fecha_creacion, fecha_modificacion, fecha, subtotal,
                    descuento, total, forma_pago, cliente_id, usuario_id, caja_id, estado_venta)
                values ('ACTIVO', '2026-01-02', '2026-01-02', '2026-01-02', 50, 0, 50, 'EFECTIVO',
                    null, 1, 1, 'PAGADA')
                """);
        assertThat(jdbc.queryForObject("select count(*) from ventas where cliente_id is null", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("No hace nada si la tabla no existe o ya está migrada")
    void idempotente() {
        migracion.migrarSiCorresponde();

        crearTablaVieja();
        migracion.migrarSiCorresponde();
        migracion.migrarSiCorresponde();

        assertThat(clienteNotNull()).isZero();
    }

}
