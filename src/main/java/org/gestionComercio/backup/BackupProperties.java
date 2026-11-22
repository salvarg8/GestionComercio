package org.gestionComercio.backup;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Configuración de las copias de seguridad (prefijo {@code backup.} en
 * application.properties).
 *
 * @param herramientas      carpeta con pg_dump y pg_restore; vacía para buscarlas
 *                          en {@code pgsql/bin} (instalador) y en el PATH
 * @param carpetaAutomatica carpeta de las copias automáticas y de las que se hacen
 *                          antes de restaurar
 * @param retencion         cantidad de copias automáticas que se conservan
 * @param automatico        true sólo en la PC servidor: hace las copias automáticas
 */
@ConfigurationProperties(prefix = "backup")
public record BackupProperties(
        String herramientas,
        Path carpetaAutomatica,
        int retencion,
        boolean automatico
) {

    public BackupProperties {
        if (carpetaAutomatica == null) {
            carpetaAutomatica = Path.of("data", "backups");
        }
        if (retencion < 1) {
            retencion = 1;
        }
    }
}
