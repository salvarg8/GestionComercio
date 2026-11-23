package org.gestionComercio.impresion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.gestionComercio.exception.BusinessException;
import org.springframework.stereotype.Component;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Impresoras instaladas en esta PC e impresión directa de un PDF, sin abrir
 * el visor. Cada página sale con su tamaño (rollo de 58/80 mm o A4); el
 * controlador de la impresora la ajusta al papel.
 */
@Component
public class ImpresoraPdf {

    /** Nombres de las impresoras instaladas, ordenados; la predeterminada primero. */
    public List<String> impresoras() {

        PrintService predeterminada = PrintServiceLookup.lookupDefaultPrintService();
        String nombrePredeterminada = predeterminada == null ? null : predeterminada.getName();

        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .map(PrintService::getName)
                .distinct()
                .sorted((a, b) -> a.equals(nombrePredeterminada) ? -1
                        : b.equals(nombrePredeterminada) ? 1 : a.compareToIgnoreCase(b))
                .toList();
    }

    /**
     * Imprime el PDF en la impresora indicada.
     *
     * @param impresora nombre de una impresora instalada
     * @param titulo    nombre del trabajo en la cola de impresión
     * @throws BusinessException si la impresora no está instalada o falla la impresión
     */
    public void imprimir(byte[] pdf, String impresora, String titulo) {

        PrintService servicio = buscar(impresora).orElseThrow(() -> new BusinessException(
                "La impresora \"" + impresora + "\" no está instalada en esta PC. "
                        + "Elija otra en Configuración del sistema."));

        try (PDDocument documento = Loader.loadPDF(pdf)) {

            PrinterJob trabajo = PrinterJob.getPrinterJob();
            trabajo.setPrintService(servicio);
            trabajo.setJobName(titulo == null || titulo.isBlank() ? "Comprobante" : titulo);
            trabajo.setPageable(new PDFPageable(documento));
            trabajo.print();

        } catch (PrinterException | IOException e) {
            throw new BusinessException("No se pudo imprimir en \"" + impresora + "\": " + e.getMessage(), e);
        }
    }

    private static Optional<PrintService> buscar(String nombre) {

        if (nombre == null) {
            return Optional.empty();
        }

        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .filter(s -> s.getName().equalsIgnoreCase(nombre))
                .findFirst();
    }

}
