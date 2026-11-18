package org.gestionComercio.util;

import javafx.scene.image.Image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

/**
 * Imágenes de la aplicación (por ahora, el logo de la empresa).
 * <p>
 * El logo elegido por el usuario se copia a {@link #CARPETA_LOGOS}, junto a la
 * base de datos: así sigue disponible aunque el archivo original se mueva o se borre.
 */
public final class ImageUtils {

    /** Carpeta donde se guardan las copias de los logos (relativa al directorio de trabajo, como la base). */
    public static final Path CARPETA_LOGOS = Path.of("data", "logos");

    public static final List<String> EXTENSIONES_PERMITIDAS = List.of("png", "jpg", "jpeg", "gif", "bmp");

    public static final long TAMANIO_MAXIMO_LOGO = 2L * 1024 * 1024;

    private ImageUtils() {
    }

    /** Extensión en minúsculas y sin punto; vacío si no tiene. */
    public static String extension(Path archivo) {

        String nombre = archivo.getFileName().toString();
        int punto = nombre.lastIndexOf('.');

        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Revisa que el archivo exista, sea de un formato de imagen admitido y no
     * supere el tamaño máximo. Devuelve el mensaje de error, o null si es válido.
     * No comprueba que el contenido sea realmente una imagen: eso lo hace {@link #cargar}.
     */
    public static String validarArchivoLogo(Path archivo) {

        if (archivo == null || !Files.isRegularFile(archivo)) {
            return "El archivo seleccionado no existe.";
        }

        if (!EXTENSIONES_PERMITIDAS.contains(extension(archivo))) {
            return "El logo debe ser una imagen PNG, JPG, GIF o BMP.";
        }

        try {
            if (Files.size(archivo) > TAMANIO_MAXIMO_LOGO) {
                return "El logo no puede superar los 2 MB.";
            }
        } catch (IOException ex) {
            return "No se pudo leer el archivo seleccionado.";
        }

        return null;
    }

    /**
     * Carga una imagen desde una ruta del disco. Devuelve null si la ruta está
     * vacía, el archivo no existe o no es una imagen que JavaFX pueda leer.
     */
    public static Image cargar(String ruta) {

        if (ruta == null || ruta.isBlank()) {
            return null;
        }

        Path archivo = Path.of(ruta);

        if (!Files.isRegularFile(archivo)) {
            return null;
        }

        // Carga sincrónica: así isError() ya es definitivo al volver.
        Image imagen = new Image(archivo.toUri().toString(), false);

        return imagen.isError() ? null : imagen;
    }

    /** Copia el logo a {@link #CARPETA_LOGOS} y devuelve la ruta de la copia. */
    public static String guardarLogo(Path origen) throws IOException {
        return guardarLogo(origen, CARPETA_LOGOS);
    }

    /**
     * Copia el logo a la carpeta indicada con un nombre nuevo (nunca pisa el
     * logo vigente) y devuelve la ruta de la copia con barras "/".
     */
    public static String guardarLogo(Path origen, Path carpeta) throws IOException {

        Files.createDirectories(carpeta);

        Path destino = carpeta.resolve("logo-" + System.currentTimeMillis() + "." + extension(origen));

        Files.copy(origen, destino, StandardCopyOption.REPLACE_EXISTING);

        return destino.toString().replace('\\', '/');
    }

    /** Borra una copia de logo que ya no se usa. */
    public static void eliminarLogoGuardado(String ruta) {
        eliminarLogoGuardado(ruta, CARPETA_LOGOS);
    }

    /**
     * Borra el archivo sólo si está dentro de la carpeta de logos: nunca toca
     * un archivo del usuario. Si no se puede borrar, se ignora (queda un archivo
     * sin uso, no afecta a la aplicación).
     */
    public static void eliminarLogoGuardado(String ruta, Path carpeta) {

        if (ruta == null || ruta.isBlank()) {
            return;
        }

        Path archivo = Path.of(ruta).toAbsolutePath().normalize();
        Path carpetaAbsoluta = carpeta.toAbsolutePath().normalize();

        if (!archivo.startsWith(carpetaAbsoluta)) {
            return;
        }

        try {
            Files.deleteIfExists(archivo);
        } catch (IOException ignored) {
            // Archivo bloqueado o sin permisos: se deja como está.
        }
    }
}
