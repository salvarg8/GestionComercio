package org.gestionComercio.migracion;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.gestionComercio.support.PostgresTestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica las migraciones de Flyway contra un PostgreSQL vacío: que alcanzan
 * para crear el esquema (Hibernate lo valida al levantar el contexto, con
 * ddl-auto=validate), que las restricciones de V1 se cumplen y que una
 * migración fallida no deja nada a medias.
 *
 * <p>Sin transacción de test: cada sentencia se confirma sola, así una
 * violación de restricción no aborta las siguientes.</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfig.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MigracionesIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private long categoriaId;
    private long marcaId;
    private long proveedorId;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from productos");
        jdbc.update("delete from categorias");
        jdbc.update("delete from marcas");
        jdbc.update("delete from proveedores");

        categoriaId = insertarMaestro("categorias", "nombre", "Bebidas");
        marcaId = insertarMaestro("marcas", "nombre", "Marca");
        proveedorId = insertarMaestro("proveedores", "razon_social", "Proveedor SA");
    }

    // ------------------------------------------------------------------
    // Esquema
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la base se crea sólo con las migraciones y queda registrada en el historial de Flyway")
    void esquemaCreadoPorFlyway() {
        List<String> versiones = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank",
                String.class);

        assertTrue(versiones.contains("1"));
        assertTrue(versiones.contains("2"));
        assertTrue(versiones.contains("3"));
        assertEquals(0, jdbc.queryForObject(
                "select count(*) from flyway_schema_history where not success", Integer.class));
    }

    // ------------------------------------------------------------------
    // Índices únicos parciales
    // ------------------------------------------------------------------

    @Test
    @DisplayName("V2 agrega los datos de anulación de ventas con sus restricciones")
    void anulacionDeVentas() {
        List<String> restricciones = jdbc.queryForList(
                "select conname from pg_constraint where conrelid = 'ventas'::regclass", String.class);

        assertTrue(restricciones.containsAll(List.of(
                "fk_ventas_usuario_anulacion", "ck_ventas_motivo_anulacion", "ck_ventas_anulacion")));
    }

    @Test
    @DisplayName("V3 agrega el comprobante de venta, único por tipo y número")
    void comprobanteDeVenta() {
        List<String> restricciones = jdbc.queryForList(
                "select conname from pg_constraint where conrelid = 'ventas'::regclass", String.class);

        assertTrue(restricciones.containsAll(List.of("uk_ventas_comprobante", "ck_ventas_numero_comprobante")));
        assertTrue(existeTabla("public", "numeradores_comprobante"));
    }

    @Test
    @DisplayName("V3 numera como tickets las ventas existentes y deja el numerador en el último número")
    void comprobanteDeVentasExistentes() {
        // En un esquema aparte: hasta V2, se cargan ventas y recién entonces se aplica V3.
        String esquema = "prueba_v3";

        try {
            flyway(esquema).target("2").load().migrate();

            // Sin controles de claves foráneas: sólo interesan las ventas.
            String venta = "('ACTIVO', now(), now(), now(), 10, 0, 10, 'EFECTIVO', 'PAGADA', 1, 1)";
            jdbc.execute("set session_replication_role = replica; "
                    + "insert into " + esquema + ".ventas (estado_registro, fecha_creacion, fecha_modificacion, "
                    + "fecha, subtotal, descuento, total, forma_pago, estado_venta, usuario_id, caja_id) "
                    + "values " + venta + ", " + venta + ", " + venta + "; "
                    + "set session_replication_role = origin");

            flyway(esquema).load().migrate();

            assertEquals(List.of(1L, 2L, 3L), jdbc.queryForList("select numero_comprobante from " + esquema
                    + ".ventas where tipo_comprobante = 'TICKET' order by id", Long.class));
            assertEquals(3L, jdbc.queryForObject("select ultimo_numero from " + esquema
                    + ".numeradores_comprobante where tipo_comprobante = 'TICKET'", Long.class));
        } finally {
            jdbc.execute("drop schema if exists " + esquema + " cascade");
        }
    }

    @Test
    @DisplayName("V6 agrega a la caja el usuario que la abrió y el que la cerró")
    void cajaDeOtroUsuario() {
        List<String> restricciones = jdbc.queryForList(
                "select conname from pg_constraint where conrelid = 'cajas'::regclass", String.class);

        assertTrue(restricciones.containsAll(List.of(
                "fk_cajas_usuario_apertura", "fk_cajas_usuario_cierre", "ck_cajas_usuario_cierre")));
    }

    @Test
    @DisplayName("V6 toma al responsable como quien abrió y cerró las cajas existentes")
    void cajaDeOtroUsuarioEnCajasExistentes() {
        // En un esquema aparte: hasta V5, se cargan cajas y recién entonces se aplica V6.
        String esquema = "prueba_v6";

        try {
            flyway(esquema).target("5").load().migrate();

            // Sin controles de claves foráneas al insertar: sólo interesan las cajas. Los usuarios
            // tienen que existir porque V6 crea claves foráneas hacia ellos.
            jdbc.execute("set session_replication_role = replica; "
                    + "insert into " + esquema + ".usuarios (id, estado_registro, fecha_creacion, "
                    + "fecha_modificacion, usuario, password, nombre, apellido, estado_usuario, empresa_id, "
                    + "rol_id, tipo_documento, documento) values "
                    + "(5, 'ACTIVO', now(), now(), 'cajero1', 'x', 'Ana', 'Pérez', 'ACTIVO', 1, 1, 'DNI', '1'), "
                    + "(6, 'ACTIVO', now(), now(), 'cajero2', 'x', 'Luis', 'Gómez', 'ACTIVO', 1, 1, 'DNI', '2'); "
                    + "insert into " + esquema + ".cajas (estado_registro, fecha_creacion, fecha_modificacion, "
                    + "fecha_apertura, saldo_inicial, estado_caja, usuario_id) values "
                    + "('ACTIVO', now(), now(), now(), 0, 'CERRADA', 5), "
                    + "('ACTIVO', now(), now(), now(), 0, 'ABIERTA', 6); "
                    + "set session_replication_role = origin");

            flyway(esquema).load().migrate();

            assertEquals(List.of(5L, 6L), jdbc.queryForList("select usuario_apertura_id from " + esquema
                    + ".cajas order by id", Long.class));
            assertEquals(Arrays.asList(5L, null), jdbc.queryForList("select usuario_cierre_id from " + esquema
                    + ".cajas order by id", Long.class));
        } finally {
            jdbc.execute("drop schema if exists " + esquema + " cascade");
        }
    }

    private FluentConfiguration flyway(String esquema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(esquema)
                .createSchemas(true)
                .locations("classpath:db/migration");
    }

    @Test
    @DisplayName("dos productos activos no pueden tener el mismo código interno, sin distinguir mayúsculas")
    void codigoUnicoEntreActivos() {
        insertarProducto("P001", null, "ACTIVO", "0", false);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertarProducto("p001", null, "ACTIVO", "0", false));
    }

    @Test
    @DisplayName("un producto dado de baja libera su código interno y su código de barras")
    void codigosLiberadosAlDarDeBaja() {
        insertarProducto("P001", "7790001000011", "INACTIVO", "0", false);
        insertarProducto("P001", "7790001000011", "INACTIVO", "0", false);

        insertarProducto("P001", "7790001000011", "ACTIVO", "0", false);

        assertEquals(3, jdbc.queryForObject("select count(*) from productos", Integer.class));
    }

    @Test
    @DisplayName("dos productos activos no pueden tener el mismo código de barras")
    void codigoBarrasUnicoEntreActivos() {
        insertarProducto("P001", "7790001000011", "ACTIVO", "0", false);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertarProducto("P002", "7790001000011", "ACTIVO", "0", false));
    }

    // ------------------------------------------------------------------
    // Restricciones CHECK
    // ------------------------------------------------------------------

    @Test
    @DisplayName("el stock no puede ser negativo salvo que el producto lo permita")
    void stockNegativo() {
        assertThrows(DataIntegrityViolationException.class,
                () -> insertarProducto("P001", null, "ACTIVO", "-1", false));

        insertarProducto("P002", null, "ACTIVO", "-1", true);
    }

    @Test
    @DisplayName("los precios de un producto no pueden ser negativos")
    void precioNegativo() {
        long id = insertarProducto("P001", null, "ACTIVO", "0", false);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update productos set precio_venta = -1 where id = ?", id));
    }

    // ------------------------------------------------------------------
    // Migración fallida
    // ------------------------------------------------------------------

    @Test
    @DisplayName("una migración que falla se revierte entera")
    void migracionFallidaSeRevierte() {
        // En un esquema aparte, para no tocar el que usan los demás tests.
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("prueba_migracion_fallida")
                .createSchemas(true)
                .locations("classpath:db/migration", "classpath:db/migracion-fallida")
                .load();

        try {
            assertThrows(FlywayException.class, flyway::migrate);

            assertFalse(existeTabla("prueba_migracion_fallida", "tabla_de_prueba"),
                    "La tabla creada por la migración fallida no debería existir");
            assertTrue(existeTabla("prueba_migracion_fallida", "productos"),
                    "Las migraciones reales deberían haberse aplicado completas");
            // V999 es la rota: la versión actual queda en la última migración real.
            assertFalse(flyway.info().current().getVersion().isAtLeast("999"));
        } finally {
            jdbc.execute("drop schema if exists prueba_migracion_fallida cascade");
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private boolean existeTabla(String esquema, String tabla) {
        return jdbc.queryForObject(
                "select exists (select 1 from information_schema.tables where table_schema = ? and table_name = ?)",
                Boolean.class, esquema, tabla);
    }

    private long insertarMaestro(String tabla, String columna, String valor) {
        return jdbc.queryForObject(
                "insert into " + tabla + " (estado_registro, fecha_creacion, fecha_modificacion, " + columna + ") "
                        + "values ('ACTIVO', now(), now(), ?) returning id",
                Long.class, valor);
    }

    private long insertarProducto(String codigo, String codigoBarras, String estado, String stock,
                                  boolean permiteStockNegativo) {
        return jdbc.queryForObject("""
                        insert into productos (
                            estado_registro, fecha_creacion, fecha_modificacion,
                            codigo, nombre, codigo_barras, precio_compra, precio_venta,
                            stock, stock_minimo, maneja_stock, permite_stock_negativo,
                            unidad_medida, alicuotaiva, categoria_id, marca_id, proveedor_id)
                        values (?, now(), now(), ?, 'Producto', ?, 0, 0,
                                cast(? as numeric), 0, true, ?,
                                'UNIDAD', 'IVA_21', ?, ?, ?)
                        returning id
                        """,
                Long.class, estado, codigo, codigoBarras, stock, permiteStockNegativo,
                categoriaId, marcaId, proveedorId);
    }
}
