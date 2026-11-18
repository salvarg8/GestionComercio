package org.gestionComercio.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sólo la parte de archivos: no requiere el toolkit de JavaFX. */
class ImageUtilsTest {

    @TempDir
    Path tmp;

    private Path archivo(String nombre, int bytes) throws IOException {
        Path archivo = tmp.resolve(nombre);
        Files.write(archivo, new byte[bytes]);
        return archivo;
    }

    @Test
    @DisplayName("extension() devuelve la extensión en minúsculas, o vacío si no tiene")
    void extension() {
        assertEquals("png", ImageUtils.extension(Path.of("logo.PNG")));
        assertEquals("jpeg", ImageUtils.extension(Path.of("mi.logo.jpeg")));
        assertEquals("", ImageUtils.extension(Path.of("logo")));
    }

    @Test
    @DisplayName("un archivo de imagen existente y chico es válido")
    void archivoValido() throws IOException {
        assertNull(ImageUtils.validarArchivoLogo(archivo("logo.png", 100)));
    }

    @Test
    @DisplayName("se rechaza un archivo que no existe")
    void archivoInexistente() {
        assertTrue(ImageUtils.validarArchivoLogo(tmp.resolve("no-existe.png")).contains("no existe"));
    }

    @Test
    @DisplayName("se rechaza un formato que no es de imagen")
    void formatoNoPermitido() throws IOException {
        assertTrue(ImageUtils.validarArchivoLogo(archivo("logo.pdf", 100)).contains("PNG"));
    }

    @Test
    @DisplayName("se rechaza un archivo de más de 2 MB")
    void archivoGrande() throws IOException {
        Path grande = archivo("logo.png", (int) ImageUtils.TAMANIO_MAXIMO_LOGO + 1);

        assertTrue(ImageUtils.validarArchivoLogo(grande).contains("2 MB"));
    }

    @Test
    @DisplayName("guardarLogo() copia el archivo a la carpeta de logos, creándola si no existe")
    void guardarLogoCopia() throws IOException {
        Path origen = tmp.resolve("original.png");
        Files.write(origen, new byte[]{1, 2, 3});
        Path carpeta = tmp.resolve("data").resolve("logos");

        String ruta = ImageUtils.guardarLogo(origen, carpeta);

        Path copia = Path.of(ruta);
        assertTrue(copia.toAbsolutePath().startsWith(carpeta.toAbsolutePath()));
        assertTrue(ruta.endsWith(".png"));
        assertFalse(ruta.contains("\\"));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(copia));
        assertTrue(Files.exists(origen));
    }

    @Test
    @DisplayName("eliminarLogoGuardado() borra una copia de la carpeta de logos")
    void eliminarCopia() throws IOException {
        Path carpeta = Files.createDirectories(tmp.resolve("logos"));
        Path copia = Files.write(carpeta.resolve("logo-1.png"), new byte[]{1});

        ImageUtils.eliminarLogoGuardado(copia.toString(), carpeta);

        assertFalse(Files.exists(copia));
    }

    @Test
    @DisplayName("eliminarLogoGuardado() nunca borra un archivo fuera de la carpeta de logos")
    void noBorraArchivosDelUsuario() throws IOException {
        Path carpeta = Files.createDirectories(tmp.resolve("logos"));
        Path delUsuario = archivo("mi-logo.png", 10);

        ImageUtils.eliminarLogoGuardado(delUsuario.toString(), carpeta);
        ImageUtils.eliminarLogoGuardado(carpeta.resolve("..").resolve("mi-logo.png").toString(), carpeta);

        assertTrue(Files.exists(delUsuario));
    }

    @Test
    @DisplayName("eliminarLogoGuardado() ignora rutas vacías")
    void rutaVacia() {
        ImageUtils.eliminarLogoGuardado(null, tmp);
        ImageUtils.eliminarLogoGuardado("  ", tmp);
    }
}
