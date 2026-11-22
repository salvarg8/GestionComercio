package org.gestionComercio.service;

import org.gestionComercio.dto.backup.ArchivoBackup;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Copias de seguridad de la base PostgreSQL con pg_dump (formato custom) y
 * restauración con pg_restore.
 */
public interface BackupService {

    /**
     * Crea una copia de la base en la carpeta indicada. Es consistente aunque
     * haya usuarios trabajando: pg_dump toma una foto de la base al empezar.
     * Requiere BACKUP_CREAR.
     *
     * @return el archivo creado
     */
    Path crearBackup(Path carpeta);

    /**
     * Crea una copia en la carpeta de copias automáticas y borra las más viejas
     * según la retención configurada. No exige permisos: la hace el sistema.
     *
     * @return el archivo creado, o vacío si hay una restauración en curso
     */
    Optional<Path> crearBackupAutomatico();

    /**
     * Reemplaza toda la base por la de la copia. Antes guarda una copia de la base
     * actual en la carpeta de copias automáticas, avisa a las demás PC y corta sus
     * conexiones. Si algo falla, la base queda como estaba. Al terminar hay que
     * reiniciar la aplicación en todas las PC. Requiere BACKUP_RESTAURAR.
     *
     * @return la copia de la base anterior que se hizo antes de restaurar
     */
    Path restaurar(Path archivo);

    /** Copias de la carpeta de copias automáticas, de la más nueva a la más vieja. */
    List<ArchivoBackup> listarBackupsAutomaticos();

    /** Fecha de la copia automática más reciente (sin contar las hechas antes de restaurar). */
    Optional<LocalDateTime> fechaUltimoBackupAutomatico();

    Path getCarpetaAutomatica();

    int getRetencion();

    /** Indica si esta PC hace las copias automáticas (la PC servidor). */
    boolean isAutomaticoHabilitado();
}
