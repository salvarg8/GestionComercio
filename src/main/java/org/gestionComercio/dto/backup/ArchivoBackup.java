package org.gestionComercio.dto.backup;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Una copia de seguridad guardada en disco.
 *
 * @param ruta              archivo .backup
 * @param fecha             fecha y hora en que se hizo (tomada del nombre del archivo)
 * @param tamanio           tamaño en bytes
 * @param antesDeRestaurar  true si es la copia que se hizo automáticamente antes de una restauración
 */
public record ArchivoBackup(Path ruta, LocalDateTime fecha, long tamanio, boolean antesDeRestaurar) {

    private static final double KB = 1024;

    public String nombre() {
        return ruta.getFileName().toString();
    }

    public String tamanioLegible() {

        if (tamanio < KB) {
            return tamanio + " B";
        }
        if (tamanio < KB * KB) {
            return String.format(Locale.ROOT, "%.1f KB", tamanio / KB);
        }
        return String.format(Locale.ROOT, "%.1f MB", tamanio / (KB * KB));
    }
}
