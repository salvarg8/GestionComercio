package org.gestionComercio.backup;

import org.gestionComercio.dto.backup.ArchivoBackup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchivosBackupTest {

    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 11, 21, 15, 30, 5);

    @TempDir
    Path carpeta;

    @Test
    @DisplayName("el nombre lleva fecha y hora, y no pisa una copia del mismo segundo")
    void nuevaRuta() throws IOException {
        Path primera = ArchivosBackup.nuevaRuta(carpeta, FECHA, false);
        assertThat(primera.getFileName()).hasToString("gestion_comercio_20261121_153005.backup");

        Files.createFile(primera);

        assertThat(ArchivosBackup.nuevaRuta(carpeta, FECHA, false).getFileName())
                .hasToString("gestion_comercio_20261121_153005_2.backup");
        assertThat(ArchivosBackup.nuevaRuta(carpeta, FECHA, true).getFileName())
                .hasToString("gestion_comercio_20261121_153005_antes_de_restaurar.backup");
    }

    @Test
    @DisplayName("lista sólo las copias, de la más nueva a la más vieja")
    void listar() throws IOException {
        crear("gestion_comercio_20261120_100000.backup");
        crear("gestion_comercio_20261121_100000_antes_de_restaurar.backup");
        crear("gestion_comercio_20261119_100000.backup");
        crear("gestion_comercio_20261122_100000.backup.parcial");
        crear("notas.txt");
        crear("otra_base_20261121_100000.backup");

        List<ArchivoBackup> copias = ArchivosBackup.listar(carpeta);

        assertThat(copias).extracting(ArchivoBackup::nombre).containsExactly(
                "gestion_comercio_20261121_100000_antes_de_restaurar.backup",
                "gestion_comercio_20261120_100000.backup",
                "gestion_comercio_20261119_100000.backup");
        assertThat(copias.get(0).antesDeRestaurar()).isTrue();
        assertThat(copias.get(1).fecha()).isEqualTo(LocalDateTime.of(2026, 11, 20, 10, 0));
    }

    @Test
    @DisplayName("una carpeta que no existe no tiene copias")
    void carpetaInexistente() {
        assertThat(ArchivosBackup.listar(carpeta.resolve("no-existe"))).isEmpty();
    }

    @Test
    @DisplayName("la retención borra las automáticas más viejas y nunca las previas a una restauración ni otros archivos")
    void retencion() throws IOException {
        for (int dia = 10; dia <= 15; dia++) {
            crear("gestion_comercio_202611" + dia + "_220000.backup");
        }
        crear("gestion_comercio_20261101_090000_antes_de_restaurar.backup");
        crear("notas.txt");

        List<Path> borrados = ArchivosBackup.aplicarRetencion(carpeta, 3);

        assertThat(borrados).extracting(p -> p.getFileName().toString()).containsExactlyInAnyOrder(
                "gestion_comercio_20261110_220000.backup",
                "gestion_comercio_20261111_220000.backup",
                "gestion_comercio_20261112_220000.backup");

        assertThat(ArchivosBackup.listar(carpeta)).extracting(ArchivoBackup::nombre).containsExactly(
                "gestion_comercio_20261115_220000.backup",
                "gestion_comercio_20261114_220000.backup",
                "gestion_comercio_20261113_220000.backup",
                "gestion_comercio_20261101_090000_antes_de_restaurar.backup");
        assertThat(carpeta.resolve("notas.txt")).exists();
    }

    @Test
    @DisplayName("tamaño legible")
    void tamanioLegible() {
        assertThat(copia(512).tamanioLegible()).isEqualTo("512 B");
        assertThat(copia(2048).tamanioLegible()).isEqualTo("2.0 KB");
        assertThat(copia(5L * 1024 * 1024).tamanioLegible()).isEqualTo("5.0 MB");
    }

    private ArchivoBackup copia(long tamanio) {
        return new ArchivoBackup(carpeta.resolve("x.backup"), FECHA, tamanio, false);
    }

    private void crear(String nombre) throws IOException {
        Files.writeString(carpeta.resolve(nombre), "x");
    }
}
