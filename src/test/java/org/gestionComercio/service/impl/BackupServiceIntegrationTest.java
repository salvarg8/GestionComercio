package org.gestionComercio.service.impl;

import org.flywaydb.core.Flyway;
import org.gestionComercio.backup.ArchivosBackup;
import org.gestionComercio.backup.BackupProperties;
import org.gestionComercio.backup.BloqueoRestauracion;
import org.gestionComercio.backup.ConexionPostgres;
import org.gestionComercio.dto.backup.ArchivoBackup;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.support.HerramientasEnContenedor;
import org.gestionComercio.support.PostgresTestcontainersConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Backup y restauración contra un PostgreSQL real (Testcontainers), con el
 * pg_dump y el pg_restore del contenedor. Cada test usa una base propia,
 * creada con las migraciones de Flyway como una instalación nueva.
 */
class BackupServiceIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(PostgresTestcontainersConfig.IMAGEN);

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @TempDir
    Path temporal;

    private Path carpetaManual;
    private Path carpetaAutomatica;
    private SesionUsuario sesion;

    @BeforeAll
    static void iniciarContenedor() {
        POSTGRES.start();
    }

    @AfterAll
    static void detenerContenedor() {
        POSTGRES.stop();
    }

    @BeforeEach
    void setUp() throws IOException {
        carpetaManual = Files.createDirectory(temporal.resolve("manual"));
        carpetaAutomatica = temporal.resolve("automaticas");

        sesion = new SesionUsuario();
        sesion.iniciarSesion(Usuario.builder().usuario("admin").build(),
                EnumSet.of(PermisoCodigo.BACKUP_CREAR, PermisoCodigo.BACKUP_RESTAURAR));
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    @DisplayName("restaurar deja la base como estaba al hacer la copia y antes guarda una copia de la actual")
    void restaurarRecuperaLosDatos() {
        Base base = nuevaBase(null);
        insertarCategoria(base, "Antes de la copia");

        Path copia = base.servicio.crearBackup(carpetaManual);

        insertarCategoria(base, "Después de la copia");
        base.jdbc.update("delete from categorias where nombre = 'Antes de la copia'");

        Path copiaPrevia = base.servicio.restaurar(copia);

        assertThat(categorias(base)).containsExactly("Antes de la copia");
        assertThat(copiaPrevia).exists().hasParent(carpetaAutomatica);
        assertThat(copiaPrevia.getFileName().toString()).contains("antes_de_restaurar");
        assertThat(esquemasDeRespaldo(base)).isEmpty();

        // La base restaurada coincide con las migraciones de esta versión.
        Flyway.configure().dataSource(base.dataSource).load().validate();

        // Y la copia previa permite deshacer la restauración.
        base.servicio.restaurar(copiaPrevia);
        assertThat(categorias(base)).containsExactly("Después de la copia");
    }

    @Test
    @DisplayName("una copia de una versión anterior queda operativa al aplicar las migraciones pendientes")
    void copiaDeVersionAnteriorEnInstalacionNueva() {
        Base origen = nuevaBase("5");
        insertarCategoria(origen, "Bebidas");
        Path copia = origen.servicio.crearBackup(carpetaManual);

        Base instalacionNueva = nuevaBase(null);
        instalacionNueva.servicio.restaurar(copia);

        // Al reiniciar, la aplicación aplica las migraciones que faltan.
        Flyway flyway = Flyway.configure().dataSource(instalacionNueva.dataSource).load();
        assertThat(flyway.migrate().migrationsExecuted).isPositive();
        flyway.validate();

        assertThat(categorias(instalacionNueva)).containsExactly("Bebidas");
    }

    @Test
    @DisplayName("una copia de una versión más nueva de la aplicación se rechaza y la base queda como estaba")
    void copiaDeVersionMasNueva() {
        Base base = nuevaBase(null);
        base.jdbc.update("""
                insert into flyway_schema_history
                    (installed_rank, version, description, type, script, installed_by, execution_time, success)
                values (999, '999', 'futura', 'SQL', 'V999__futura.sql', 'test', 0, true)""");
        Path copia = base.servicio.crearBackup(carpetaManual);

        base.jdbc.update("delete from flyway_schema_history where version = '999'");
        insertarCategoria(base, "Actual");

        assertThatThrownBy(() -> base.servicio.restaurar(copia))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("versión más nueva");

        assertThat(categorias(base)).containsExactly("Actual");
        assertThat(esquemasDeRespaldo(base)).isEmpty();
        Flyway.configure().dataSource(base.dataSource).load().validate();
    }

    @Test
    @DisplayName("un archivo que no es una copia se rechaza sin tocar la base")
    void archivoInvalido() throws IOException {
        Base base = nuevaBase(null);
        insertarCategoria(base, "Actual");
        Path basura = Files.writeString(carpetaManual.resolve("basura.backup"), "no es una copia");

        assertThatThrownBy(() -> base.servicio.restaurar(basura))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no es una copia de seguridad válida");

        assertThat(categorias(base)).containsExactly("Actual");
        assertThat(carpetaAutomatica).doesNotExist();
    }

    @Test
    @DisplayName("las copias automáticas conservan sólo las últimas según la retención")
    void retencionDeCopiasAutomaticas() {
        Base base = nuevaBase(null, 2);

        for (int i = 0; i < 3; i++) {
            assertThat(base.servicio.crearBackupAutomatico()).isPresent();
        }

        List<ArchivoBackup> copias = ArchivosBackup.listar(carpetaAutomatica);
        assertThat(copias).hasSize(2);
        assertThat(base.servicio.fechaUltimoBackupAutomatico()).contains(copias.get(0).fecha());
    }

    @Test
    @DisplayName("mientras una PC restaura, las demás lo ven y no pueden empezar otra restauración")
    void restauracionEnOtraPc() throws Exception {
        Base base = nuevaBase(null);
        Path copia = base.servicio.crearBackup(carpetaManual);

        BloqueoRestauracion otraPc = new BloqueoRestauracion(base.dataSource);
        BloqueoRestauracion estaPc = new BloqueoRestauracion(base.dataSource);

        try (Connection conexionOtraPc = base.dataSource.getConnection()) {

            assertThat(otraPc.tomar(conexionOtraPc)).isTrue();

            assertThat(estaPc.restauracionEnOtraPc()).isTrue();
            assertThatThrownBy(() -> base.servicio.restaurar(copia))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Ya hay una restauración en curso");

            otraPc.liberar(conexionOtraPc);
        }

        assertThat(estaPc.restauracionEnCurso()).isFalse();
    }

    @Test
    @DisplayName("la restauración corta las conexiones de las demás PC")
    void restaurarCortaLasDemasConexiones() throws Exception {
        Base base = nuevaBase(null);
        Path copia = base.servicio.crearBackup(carpetaManual);

        try (Connection otraPc = base.dataSource.getConnection()) {

            base.servicio.restaurar(copia);

            assertThat(otraPc.isValid(2)).isFalse();
        }
    }

    @Test
    @DisplayName("sin permiso no se puede crear ni restaurar")
    void sinPermiso() throws IOException {
        Base base = nuevaBase(null);
        Path copia = base.servicio.crearBackup(carpetaManual);

        sesion.iniciarSesion(Usuario.builder().usuario("cajero").build(), EnumSet.of(PermisoCodigo.VENTA_CREAR));

        assertThatThrownBy(() -> base.servicio.crearBackup(carpetaManual))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> base.servicio.restaurar(copia))
                .isInstanceOf(AuthorizationException.class);
    }

    // ------------------------------------------------------------------
    // Soporte
    // ------------------------------------------------------------------

    private record Base(DataSource dataSource, JdbcTemplate jdbc, BackupServiceImpl servicio) {
    }

    private Base nuevaBase(String versionFlyway) {
        return nuevaBase(versionFlyway, 10);
    }

    /** Crea una base vacía y le aplica las migraciones (hasta la versión indicada, o todas). */
    private Base nuevaBase(String versionFlyway, int retencion) {

        String nombre = "backup_" + SECUENCIA.incrementAndGet();

        new JdbcTemplate(dataSource(POSTGRES.getDatabaseName())).execute("create database " + nombre);

        DataSource dataSource = dataSource(nombre);

        var flyway = Flyway.configure().dataSource(dataSource);
        if (versionFlyway != null) {
            flyway.target(versionFlyway);
        }
        flyway.load().migrate();

        ConexionPostgres conexion = new ConexionPostgres(POSTGRES.getHost(), POSTGRES.getFirstMappedPort(),
                nombre, POSTGRES.getUsername(), POSTGRES.getPassword());

        BackupServiceImpl servicio = new BackupServiceImpl(
                dataSource,
                new HerramientasEnContenedor(POSTGRES),
                new BloqueoRestauracion(dataSource),
                new BackupProperties(null, carpetaAutomatica, retencion, true),
                sesion,
                conexion);
        servicio.setEsperaAviso(Duration.ZERO);

        return new Base(dataSource, new JdbcTemplate(dataSource), servicio);
    }

    private static DataSource dataSource(String base) {
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getFirstMappedPort() + "/" + base;
        return new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void insertarCategoria(Base base, String nombre) {
        base.jdbc.update("insert into categorias (estado_registro, fecha_creacion, fecha_modificacion, nombre) "
                + "values ('ACTIVO', now(), now(), ?)", nombre);
    }

    private static List<String> categorias(Base base) {
        return base.jdbc.queryForList("select nombre from categorias order by id", String.class);
    }

    private static List<String> esquemasDeRespaldo(Base base) {
        return base.jdbc.queryForList(
                "select nspname from pg_namespace where nspname like 'respaldo_restauracion_%'", String.class);
    }
}
