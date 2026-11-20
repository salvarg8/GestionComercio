package org.gestionComercio.controller.venta;

import javafx.application.HostServices;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.service.ComprobanteVentaService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Genera el comprobante de una venta y lo abre con el visor de PDF del
 * sistema, desde donde se imprime o se guarda. El archivo queda en la
 * carpeta temporal del usuario.
 */
@Component
@RequiredArgsConstructor
public class ImpresionComprobante {

    private static final Path CARPETA = Path.of(System.getProperty("java.io.tmpdir"),
            "gestion-comercio", "comprobantes");

    private final ComprobanteVentaService comprobanteVentaService;
    private final HostServices hostServices;

    /**
     * @throws BusinessException si no se puede guardar el PDF
     */
    public void imprimir(VentaDto venta) {

        byte[] pdf = comprobanteVentaService.generarPdf(venta.getId());

        Path archivo = guardar(pdf, nombreArchivo(venta));

        hostServices.showDocument(archivo.toUri().toString());
    }

    /** "Ticket-00000012" a partir de "Ticket N° 00000012"; "Venta-ID" si no tiene comprobante. */
    static String nombreArchivo(VentaDto venta) {

        String comprobante = venta.getComprobante();

        String base = comprobante == null || comprobante.isBlank()
                ? "Venta-" + venta.getId()
                : comprobante.replace("N°", "");

        return base.trim().replaceAll("[^\\p{L}\\p{N}]+", "-");
    }

    /**
     * Si el PDF de una impresión anterior sigue abierto en el visor (Windows
     * lo bloquea), se guarda con otro nombre.
     */
    private static Path guardar(byte[] pdf, String nombre) {

        try {
            Files.createDirectories(CARPETA);

            Path archivo = CARPETA.resolve(nombre + ".pdf");

            try {
                return Files.write(archivo, pdf);
            } catch (IOException bloqueado) {
                return Files.write(CARPETA.resolve(nombre + "-" + System.currentTimeMillis() + ".pdf"), pdf);
            }

        } catch (IOException e) {
            throw new BusinessException("No se pudo guardar el comprobante en " + CARPETA + ": " + e.getMessage());
        }
    }
}
