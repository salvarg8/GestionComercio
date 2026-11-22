package org.gestionComercio.backup;

import org.gestionComercio.dto.backup.ArchivoBackup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Nombres, listado y retención de los archivos de copia de seguridad.
 *
 * <p>Los archivos se llaman {@code gestion_comercio_AAAAMMDD_HHMMSS.backup};
 * la copia que se hace antes de restaurar lleva además el sufijo
 * {@code _antes_de_restaurar}. Sólo se listan y borran archivos con ese
 * formato: nunca se toca otro archivo de la carpeta.</p>
 */
public final class ArchivosBackup {

    public static final String EXTENSION = ".backup";

    static final String PREFIJO = "gestion_comercio_";
    public static final String SUFIJO_ANTES_DE_RESTAURAR = "_antes_de_restaurar";

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final Pattern PATRON = Pattern.compile(
            "^" + PREFIJO + "(\\d{8}_\\d{6})(?:_\\d+)?(" + SUFIJO_ANTES_DE_RESTAURAR + ")?"
                    + Pattern.quote(EXTENSION) + "$");

    private ArchivosBackup() {
    }

    /**
     * Ruta libre para una copia nueva en la carpeta. Si ya existe una del mismo
     * segundo, agrega un número para no pisarla.
     */
    public static Path nuevaRuta(Path carpeta, LocalDateTime fecha, boolean antesDeRestaurar) {

        String base = PREFIJO + FORMATO_FECHA.format(fecha);
        String sufijo = (antesDeRestaurar ? SUFIJO_ANTES_DE_RESTAURAR : "") + EXTENSION;

        Path ruta = carpeta.resolve(base + sufijo);

        for (int i = 2; Files.exists(ruta); i++) {
            ruta = carpeta.resolve(base + "_" + i + sufijo);
        }

        return ruta;
    }

    /** Copias de la carpeta, de la más nueva a la más vieja. Vacío si la carpeta no existe. */
    public static List<ArchivoBackup> listar(Path carpeta) {

        if (!Files.isDirectory(carpeta)) {
            return List.of();
        }

        try (Stream<Path> archivos = Files.list(carpeta)) {

            return archivos
                    .filter(Files::isRegularFile)
                    .map(ArchivosBackup::leer)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(ArchivoBackup::fecha)
                            .thenComparing(ArchivoBackup::nombre)
                            .reversed())
                    .toList();

        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Borra las copias automáticas más viejas y deja las {@code conservar} más
     * nuevas. Las copias hechas antes de restaurar no se cuentan ni se borran.
     *
     * @return los archivos borrados
     */
    public static List<Path> aplicarRetencion(Path carpeta, int conservar) {

        List<ArchivoBackup> automaticas = listar(carpeta).stream()
                .filter(archivo -> !archivo.antesDeRestaurar())
                .toList();

        List<Path> borrados = new ArrayList<>();

        for (ArchivoBackup archivo : automaticas.subList(Math.min(conservar, automaticas.size()),
                automaticas.size())) {
            try {
                Files.deleteIfExists(archivo.ruta());
                borrados.add(archivo.ruta());
            } catch (IOException ex) {
                // Si no se puede borrar (por ejemplo, está abierto), se intenta en la próxima copia.
            }
        }

        return borrados;
    }

    static Optional<ArchivoBackup> leer(Path ruta) {

        Matcher matcher = PATRON.matcher(ruta.getFileName().toString());

        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            LocalDateTime fecha = LocalDateTime.parse(matcher.group(1), FORMATO_FECHA);
            long tamanio = Files.size(ruta);
            return Optional.of(new ArchivoBackup(ruta, fecha, tamanio, matcher.group(2) != null));
        } catch (DateTimeParseException | IOException ex) {
            return Optional.empty();
        }
    }
}
