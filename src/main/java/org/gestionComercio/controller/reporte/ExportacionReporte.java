package org.gestionComercio.controller.reporte;

import javafx.application.HostServices;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.reporte.Reporte;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.report.ReporteExcel;
import org.gestionComercio.report.ReportePdf;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Function;

/**
 * Exporta un reporte ya generado a PDF o Excel: pregunta dónde guardarlo y
 * lo abre con la aplicación del sistema.
 */
@Component
@RequiredArgsConstructor
public class ExportacionReporte {

    private static final DateTimeFormatter FORMATO_ARCHIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    private final HostServices hostServices;

    /** Última carpeta elegida, para proponerla en la próxima exportación. */
    private File ultimaCarpeta;

    /**
     * @return el archivo guardado, o vacío si el usuario canceló
     * @throws BusinessException si no se puede guardar
     */
    public Optional<Path> exportarPdf(Reporte reporte, Window ventana) {
        return exportar(reporte, ventana, "PDF", "pdf", ReportePdf::generar);
    }

    /**
     * @return el archivo guardado, o vacío si el usuario canceló
     * @throws BusinessException si no se puede guardar
     */
    public Optional<Path> exportarExcel(Reporte reporte, Window ventana) {
        return exportar(reporte, ventana, "Excel", "xlsx", ReporteExcel::generar);
    }

    private Optional<Path> exportar(Reporte reporte, Window ventana, String formato, String extension,
                                    Function<Reporte, byte[]> generador) {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar a " + formato);
        chooser.setInitialFileName(nombreArchivo(reporte) + "." + extension);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                formato + " (*." + extension + ")", "*." + extension));

        if (ultimaCarpeta != null && ultimaCarpeta.isDirectory()) {
            chooser.setInitialDirectory(ultimaCarpeta);
        }

        File elegido = chooser.showSaveDialog(ventana);

        if (elegido == null) {
            return Optional.empty();
        }

        Path archivo = conExtension(elegido.toPath(), extension);
        ultimaCarpeta = archivo.toAbsolutePath().getParent().toFile();

        byte[] contenido = generador.apply(reporte);

        try {
            Files.write(archivo, contenido);
        } catch (IOException e) {
            throw new BusinessException("No se pudo guardar el archivo " + archivo.getFileName()
                    + ". Si está abierto en otro programa, ciérrelo e intente de nuevo.");
        }

        hostServices.showDocument(archivo.toUri().toString());

        return Optional.of(archivo);
    }

    /** "Ventas-por-periodo_2026-11-21_1430": título sin acentos ni espacios y fecha de generación. */
    static String nombreArchivo(Reporte reporte) {

        String titulo = Normalizer.normalize(reporte.titulo(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{Alnum}]+", "-")
                .replaceAll("(^-|-$)", "");

        return titulo + "_" + FORMATO_ARCHIVO.format(reporte.generado());
    }

    private static Path conExtension(Path archivo, String extension) {

        String nombre = archivo.getFileName().toString();

        return nombre.toLowerCase().endsWith("." + extension)
                ? archivo
                : archivo.resolveSibling(nombre + "." + extension);
    }
}
