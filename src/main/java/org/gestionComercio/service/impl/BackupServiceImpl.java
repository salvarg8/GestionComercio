package org.gestionComercio.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.api.MigrationVersion;
import org.gestionComercio.backup.ArchivosBackup;
import org.gestionComercio.backup.BackupProperties;
import org.gestionComercio.backup.BloqueoRestauracion;
import org.gestionComercio.backup.ConexionPostgres;
import org.gestionComercio.backup.HerramientasPostgres;
import org.gestionComercio.backup.ResultadoComando;
import org.gestionComercio.dto.backup.ArchivoBackup;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.BackupService;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class BackupServiceImpl implements BackupService {

    /** Tabla que tiene toda base de la aplicación: identifica una copia propia y su versión. */
    private static final String TABLA_FLYWAY = "flyway_schema_history";

    private static final DateTimeFormatter FORMATO_ESQUEMA = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * Tiempo mínimo entre el aviso de restauración y el corte de las conexiones,
     * para que las demás PC (que lo revisan cada pocos segundos) lleguen a mostrarlo.
     */
    private static final Duration ESPERA_AVISO = Duration.ofSeconds(6);

    private final DataSource dataSource;
    private final HerramientasPostgres herramientas;
    private final BloqueoRestauracion bloqueo;
    private final BackupProperties properties;
    private final SesionUsuario sesionUsuario;
    private final ConexionPostgres conexion;

    private Duration esperaAviso = ESPERA_AVISO;

    public BackupServiceImpl(DataSource dataSource,
                             HerramientasPostgres herramientas,
                             BloqueoRestauracion bloqueo,
                             BackupProperties properties,
                             SesionUsuario sesionUsuario,
                             ConexionPostgres conexion) {
        this.dataSource = dataSource;
        this.herramientas = herramientas;
        this.bloqueo = bloqueo;
        this.properties = properties;
        this.sesionUsuario = sesionUsuario;
        this.conexion = conexion;
    }

    /** Para los tests: evita esperar el aviso a otras PC. */
    void setEsperaAviso(Duration esperaAviso) {
        this.esperaAviso = esperaAviso;
    }

    // ------------------------------------------------------------------
    // Backup
    // ------------------------------------------------------------------

    @Override
    public Path crearBackup(Path carpeta) {

        sesionUsuario.requerirPermiso(PermisoCodigo.BACKUP_CREAR);

        if (carpeta == null) {
            throw new BusinessException("Elija la carpeta donde guardar la copia de seguridad.");
        }
        if (!Files.isDirectory(carpeta)) {
            throw new BusinessException("La carpeta " + carpeta + " no existe.");
        }

        return volcar(carpeta, false);
    }

    @Override
    public Optional<Path> crearBackupAutomatico() {

        if (bloqueo.isRestaurandoAqui() || bloqueo.restauracionEnCurso()) {
            return Optional.empty();
        }

        Path archivo = volcar(carpetaAutomaticaCreada(), false);

        List<Path> borrados = ArchivosBackup.aplicarRetencion(properties.carpetaAutomatica(), properties.retencion());
        borrados.forEach(borrado -> log.info("Copia automática vieja borrada: {}", borrado));

        return Optional.of(archivo);
    }

    /**
     * Ejecuta pg_dump. Escribe primero en un archivo ".parcial" y lo renombra al
     * terminar, así una copia cortada a la mitad nunca parece válida.
     */
    private Path volcar(Path carpeta, boolean antesDeRestaurar) {

        Path destino = ArchivosBackup.nuevaRuta(carpeta, LocalDateTime.now(), antesDeRestaurar);
        Path parcial = destino.resolveSibling(destino.getFileName() + ".parcial");

        ResultadoComando resultado = herramientas.ejecutar(HerramientasPostgres.PG_DUMP, List.of(
                "--format=custom",
                "--no-password",
                "--file=" + parcial,
                "--dbname=" + conexion.base()
        ), conexion.entorno());

        try {
            if (!resultado.exitoso()) {
                Files.deleteIfExists(parcial);
                log.error("pg_dump falló ({}): {}", conexion, resultado.salida());
                throw new BusinessException("No se pudo crear la copia de seguridad:\n" + resultado.resumen());
            }

            Files.move(parcial, destino, StandardCopyOption.ATOMIC_MOVE);

        } catch (IOException ex) {
            throw new BusinessException("No se pudo guardar la copia de seguridad en " + carpeta
                    + ": " + ex.getMessage(), ex);
        }

        log.info("Copia de seguridad creada: {}", destino);

        return destino;
    }

    // ------------------------------------------------------------------
    // Restauración
    // ------------------------------------------------------------------

    @Override
    public Path restaurar(Path archivo) {

        sesionUsuario.requerirPermiso(PermisoCodigo.BACKUP_RESTAURAR);

        validarCopia(archivo);

        try (Connection principal = dataSource.getConnection()) {

            principal.setAutoCommit(true);

            if (!bloqueo.tomar(principal)) {
                throw new BusinessException(
                        "Ya hay una restauración en curso desde otra PC. Espere a que termine.");
            }

            try {
                long inicio = System.nanoTime();

                Path copiaPrevia = volcar(carpetaAutomaticaCreada(), true);

                esperarAviso(inicio);

                int cortadas = bloqueo.desconectarOtrasSesiones(principal);
                log.info("Restauración de {}: {} conexiones cortadas", archivo, cortadas);

                restaurarEnEsquemaNuevo(principal, archivo);

                log.info("Restauración terminada: {} (copia previa en {})", archivo, copiaPrevia);

                return copiaPrevia;

            } finally {
                bloqueo.liberar(principal);
            }

        } catch (SQLException ex) {
            log.error("Error de base de datos al restaurar {}", archivo, ex);
            throw new BusinessException("No se pudo restaurar la copia: " + ex.getMessage(), ex);
        }
    }

    /** Antes de tocar nada: el archivo existe y es una copia de esta aplicación. */
    private void validarCopia(Path archivo) {

        if (archivo == null) {
            throw new BusinessException("Elija la copia de seguridad a restaurar.");
        }
        if (!Files.isRegularFile(archivo)) {
            throw new BusinessException("No se encontró el archivo " + archivo + ".");
        }

        ResultadoComando contenido = herramientas.ejecutar(HerramientasPostgres.PG_RESTORE,
                List.of("--list", archivo.toString()), conexion.entorno());

        if (!contenido.exitoso()) {
            throw new BusinessException("El archivo " + archivo.getFileName()
                    + " no es una copia de seguridad válida o está dañado.");
        }
        if (!contenido.salida().contains(TABLA_FLYWAY)) {
            throw new BusinessException("El archivo " + archivo.getFileName()
                    + " no es una copia de seguridad de esta aplicación.");
        }
    }

    private void esperarAviso(long inicioNanos) {

        long restante = esperaAviso.toMillis() - Duration.ofNanos(System.nanoTime() - inicioNanos).toMillis();

        if (restante <= 0) {
            return;
        }

        try {
            Thread.sleep(restante);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Se interrumpió la restauración antes de empezar.", ex);
        }
    }

    /**
     * Renombra el esquema actual a uno de respaldo, restaura la copia en un
     * esquema public vacío (en una sola transacción) y recién entonces borra el
     * respaldo. Si la restauración falla, o la copia es de una versión más nueva
     * de la aplicación, se descarta lo restaurado y vuelve el esquema original.
     *
     * <p>Restaurar sobre un esquema vacío (en lugar de pg_restore --clean) evita
     * que queden tablas que no están en la copia, que harían fallar las
     * migraciones de Flyway al reiniciar.</p>
     */
    private void restaurarEnEsquemaNuevo(Connection principal, Path archivo) throws SQLException {

        String respaldo = "respaldo_restauracion_" + FORMATO_ESQUEMA.format(LocalDateTime.now());

        MigrationVersion versionActual = versionFlyway(principal);

        enTransaccion(principal,
                "alter schema public rename to " + respaldo,
                "create schema public");

        boolean restaurada = false;

        try {
            ResultadoComando resultado = herramientas.ejecutar(HerramientasPostgres.PG_RESTORE, List.of(
                    "--dbname=" + conexion.base(),
                    "--no-password",
                    "--single-transaction",
                    "--no-owner",
                    "--no-privileges",
                    "--schema=public",
                    archivo.toString()
            ), conexion.entorno());

            if (!resultado.exitoso()) {
                log.error("pg_restore falló: {}", resultado.salida());
                throw new BusinessException("No se pudo restaurar la copia. La base quedó como estaba.\n"
                        + resultado.resumen());
            }

            MigrationVersion versionRestaurada = versionFlyway(principal);

            if (versionRestaurada == null) {
                throw new BusinessException("La copia no tiene el historial de migraciones de la base. "
                        + "La base quedó como estaba.");
            }
            if (versionActual != null && versionRestaurada.compareTo(versionActual) > 0) {
                throw new BusinessException("La copia es de una versión más nueva de la aplicación (base "
                        + versionRestaurada + ", esta versión llega hasta " + versionActual + "). "
                        + "Actualice la aplicación antes de restaurarla. La base quedó como estaba.");
            }

            restaurada = true;

        } finally {
            if (restaurada) {
                borrarRespaldo(principal, respaldo);
            } else {
                volverAlEsquemaAnterior(principal, respaldo);
            }
        }
    }

    private void volverAlEsquemaAnterior(Connection principal, String respaldo) {
        try {
            enTransaccion(principal,
                    "drop schema if exists public cascade",
                    "alter schema " + respaldo + " rename to public");
        } catch (SQLException ex) {
            log.error("No se pudo volver al esquema {} después de una restauración fallida", respaldo, ex);
            throw new BusinessException("La restauración falló y no se pudo volver a la base anterior. "
                    + "Los datos anteriores están en el esquema \"" + respaldo + "\" de la base. "
                    + "No use la aplicación y comuníquese con soporte.", ex);
        }
    }

    /** Versión más alta aplicada con éxito según el historial de Flyway del esquema public. */
    private MigrationVersion versionFlyway(Connection principal) throws SQLException {

        try (Statement st = principal.createStatement()) {

            try (ResultSet rs = st.executeQuery("select to_regclass('public." + TABLA_FLYWAY + "') is not null")) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    return null;
                }
            }

            MigrationVersion maxima = null;

            try (ResultSet rs = st.executeQuery("select version from public." + TABLA_FLYWAY
                    + " where success and version is not null")) {
                while (rs.next()) {
                    MigrationVersion version = MigrationVersion.fromVersion(rs.getString(1));
                    if (maxima == null || version.compareTo(maxima) > 0) {
                        maxima = version;
                    }
                }
            }

            return maxima;
        }
    }

    private static void enTransaccion(Connection principal, String... sentencias) throws SQLException {

        principal.setAutoCommit(false);

        try (Statement st = principal.createStatement()) {
            for (String sentencia : sentencias) {
                st.execute(sentencia);
            }
            principal.commit();
        } catch (SQLException ex) {
            principal.rollback();
            throw ex;
        } finally {
            principal.setAutoCommit(true);
        }
    }

    /** La restauración ya está confirmada: si el respaldo no se puede borrar, sólo ocupa lugar. */
    private static void borrarRespaldo(Connection principal, String respaldo) {
        try (Statement st = principal.createStatement()) {
            st.execute("drop schema " + respaldo + " cascade");
        } catch (SQLException ex) {
            log.warn("No se pudo borrar el esquema de respaldo {}", respaldo, ex);
        }
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Override
    public List<ArchivoBackup> listarBackupsAutomaticos() {

        sesionUsuario.requerirAlgunPermiso(PermisoCodigo.BACKUP_CREAR, PermisoCodigo.BACKUP_RESTAURAR);

        return new ArrayList<>(ArchivosBackup.listar(properties.carpetaAutomatica()));
    }

    @Override
    public Optional<LocalDateTime> fechaUltimoBackupAutomatico() {

        return ArchivosBackup.listar(properties.carpetaAutomatica()).stream()
                .filter(archivo -> !archivo.antesDeRestaurar())
                .map(ArchivoBackup::fecha)
                .findFirst();
    }

    @Override
    public Path getCarpetaAutomatica() {
        return properties.carpetaAutomatica().toAbsolutePath().normalize();
    }

    @Override
    public int getRetencion() {
        return properties.retencion();
    }

    @Override
    public boolean isAutomaticoHabilitado() {
        return properties.automatico();
    }

    private Path carpetaAutomaticaCreada() {
        try {
            return Files.createDirectories(properties.carpetaAutomatica());
        } catch (IOException ex) {
            throw new BusinessException("No se pudo crear la carpeta de copias automáticas "
                    + properties.carpetaAutomatica() + ": " + ex.getMessage(), ex);
        }
    }
}
