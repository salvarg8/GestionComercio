package org.gestionComercio.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.CuitUtils;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.DateUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.gestionComercio.report.ComprobanteVentaPdf.ANULADA;
import static org.gestionComercio.report.ComprobanteVentaPdf.LEYENDA_NO_FISCAL;
import static org.gestionComercio.report.ComprobanteVentaPdf.ancho;
import static org.gestionComercio.report.ComprobanteVentaPdf.cargarLogo;
import static org.gestionComercio.report.ComprobanteVentaPdf.limpiar;
import static org.gestionComercio.report.ComprobanteVentaPdf.lineasNoVacias;
import static org.gestionComercio.report.ComprobanteVentaPdf.partir;
import static org.gestionComercio.report.ComprobanteVentaPdf.prefijo;
import static org.gestionComercio.report.ComprobanteVentaPdf.recortar;
import static org.gestionComercio.report.ComprobanteVentaPdf.unir;

/**
 * Ticket en rollo de impresora térmica: el ancho configurado en el sistema
 * (58 u 80 mm) y el largo que necesite el contenido, en una sola página y
 * una sola columna.
 *
 * <p>Primero se arma la lista de renglones de texto y después se dibuja: así
 * se conoce el alto total antes de crear la página.
 */
final class TicketPdf {

    private static final float PUNTOS_POR_MM = 72f / 25.4f;
    private static final float MARGEN = 6;

    private static final float LOGO_ANCHO_MAX = 110;
    private static final float LOGO_ALTO_MAX = 45;

    private static final float CHICO = 7;
    private static final float NORMAL = 8;
    private static final float GRANDE = 11;

    private static final Color ROJO = new Color(190, 30, 30);

    private enum Alineacion { IZQUIERDA, CENTRO }

    /** Un renglón del ticket: texto a la izquierda o centrado, y opcionalmente un importe a la derecha. */
    private record Renglon(String texto, String derecha, PDFont fuente, float tamanio,
                           Alineacion alineacion, Color color, boolean separador) {

        float alto() {
            return separador ? 8 : tamanio + 3;
        }
    }

    private final VentaDto venta;
    private final EmpresaDto empresa;
    private final ClienteDto cliente;

    /** Ancho de la página y ancho útil (sin márgenes), en puntos. */
    private final float ancho;
    private final float util;

    private final PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private final List<Renglon> renglones = new ArrayList<>();

    private final OpcionesComprobante opciones;

    /**
     * @param opciones con un formato de ticket térmico
     * @throws IllegalArgumentException si el formato no es de ticket térmico
     */
    TicketPdf(VentaDto venta, EmpresaDto empresa, ClienteDto cliente, OpcionesComprobante opciones) {
        this.venta = Objects.requireNonNull(venta, "venta");
        this.empresa = Objects.requireNonNull(empresa, "empresa");
        this.cliente = cliente;
        this.opciones = Objects.requireNonNull(opciones, "opciones");

        if (!opciones.formatoTicket().esTermico()) {
            throw new IllegalArgumentException("El formato " + opciones.formatoTicket() + " no es de ticket térmico.");
        }

        this.ancho = opciones.formatoTicket().getMilimetros() * PUNTOS_POR_MM;
        this.util = ancho - 2 * MARGEN;
    }

    byte[] generar() throws IOException {

        try (PDDocument documento = new PDDocument()) {

            documento.getDocumentInformation().setTitle(venta.getComprobante());
            documento.getDocumentInformation().setAuthor(empresa.getRazonSocial());

            PDImageXObject logo = opciones.imprimirLogo() ? cargarLogo(empresa.getLogo(), documento) : null;
            float[] tamanioLogo = logo == null ? new float[]{0, 0} : tamanioLogo(logo);

            armar();

            float alto = 2 * MARGEN + tamanioLogo[1] + (logo == null ? 0 : 6)
                    + (float) renglones.stream().mapToDouble(Renglon::alto).sum();

            PDPage pagina = new PDPage(new PDRectangle(ancho, alto));
            documento.addPage(pagina);

            try (PDPageContentStream cs = new PDPageContentStream(documento, pagina)) {

                float y = alto - MARGEN;

                if (logo != null) {
                    cs.drawImage(logo, (ancho - tamanioLogo[0]) / 2, y - tamanioLogo[1],
                            tamanioLogo[0], tamanioLogo[1]);
                    y -= tamanioLogo[1] + 6;
                }

                for (Renglon renglon : renglones) {
                    dibujar(cs, renglon, y);
                    y -= renglon.alto();
                }

                if (venta.getEstadoVenta() == EstadoVenta.ANULADA) {
                    marcaDeAgua(cs, alto);
                }
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // Contenido
    // ------------------------------------------------------------------

    private void armar() throws IOException {

        // Comercio
        centrado(negrita, 10, empresa.getRazonSocial());

        for (String linea : lineasNoVacias(
                empresa.getNombreFantasia(),
                empresa.getDireccion(),
                unir(" · ", prefijo("Tel. ", empresa.getTelefono()), empresa.getEmail()),
                prefijo("CUIT: ", CuitUtils.formatear(empresa.getCuit())),
                prefijo("IVA: ", empresa.getCondicionIva() == null ? null : empresa.getCondicionIva().getDescripcion()),
                prefijo("Ingresos Brutos: ", empresa.getIngresosBrutos()))) {
            centrado(normal, CHICO, linea);
        }

        separador();

        // Comprobante
        String tipo = venta.getTipoComprobante() == null ? "Comprobante" : venta.getTipoComprobante().getDescripcion();
        centrado(negrita, GRANDE, tipo.toUpperCase());
        centrado(negrita, 9, numero());
        centrado(normal, NORMAL, "Fecha: " + DateUtils.format(venta.getFecha()));

        separador();

        // Venta
        String nombreCliente = cliente == null
                ? Objects.toString(venta.getClienteNombre(), VentaDto.CONSUMIDOR_FINAL)
                : cliente.getNombreCompleto();
        izquierda(normal, NORMAL, "Cliente: " + nombreCliente);

        if (cliente != null) {

            if (cliente.getNumeroDocumento() != null) {
                izquierda(normal, NORMAL, unir(" ", Objects.toString(cliente.getTipoDocumento(), ""),
                        cliente.getDocumentoFormateado()));
            }

            if (cliente.getCondicionIva() != null) {
                izquierda(normal, NORMAL, "IVA: " + cliente.getCondicionIva().getDescripcion());
            }

            if (cliente.getDireccion() != null && !cliente.getDireccion().isBlank()) {
                izquierda(normal, NORMAL, cliente.getDireccion());
            }
        }

        izquierda(normal, NORMAL, "Forma de pago: " + Objects.toString(venta.getFormaPago(), ""));

        String vendedor = venta.getUsuarioNombreCompleto() == null || venta.getUsuarioNombreCompleto().isBlank()
                ? venta.getUsuarioNombre() : venta.getUsuarioNombreCompleto();
        izquierda(normal, NORMAL, "Vendedor: " + Objects.toString(vendedor, ""));

        if (venta.getEstadoVenta() == EstadoVenta.ANULADA) {
            separador();
            agregar(new Renglon("*** " + ANULADA + " ***", null, negrita, GRANDE, Alineacion.CENTRO, ROJO, false));
            centrado(normal, CHICO, DateUtils.format(venta.getFechaAnulacion())
                    + Objects.toString(prefijo(" por ", venta.getUsuarioAnulacionNombreCompleto()), ""));
            izquierda(normal, CHICO, "Motivo: " + Objects.toString(venta.getMotivoAnulacion(), ""));
        }

        separador();

        // Renglones: descripción y, debajo, cantidad × precio con el subtotal a la derecha.
        List<DetalleVentaDto> detalles = Objects.requireNonNullElse(venta.getDetalles(), List.of());

        for (DetalleVentaDto d : detalles) {
            izquierda(normal, NORMAL, unir(" ", d.getProductoNombre(), d.getProductoCodigo() == null
                    ? null : "(" + d.getProductoCodigo() + ")"));
            agregar(new Renglon("   " + CantidadUtils.formatear(d.getCantidad()) + " x "
                    + CurrencyUtils.format(d.getPrecioUnitario()), CurrencyUtils.format(d.getSubtotal()),
                    normal, NORMAL, Alineacion.IZQUIERDA, Color.BLACK, false));
        }

        if (detalles.isEmpty()) {
            izquierda(normal, NORMAL, "La venta no tiene renglones.");
        }

        separador();

        importe(normal, NORMAL, "Subtotal", venta.getSubtotal());
        importe(normal, NORMAL, "Descuento", venta.getDescuento());
        importe(negrita, GRANDE, "TOTAL", venta.getTotal());

        separador();

        centrado(normal, CHICO, LEYENDA_NO_FISCAL);

        // Texto de pie de la configuración: horario, redes, políticas de cambio.
        if (!opciones.lineasPie().isEmpty()) {

            separador();

            for (String linea : opciones.lineasPie()) {
                centrado(normal, CHICO, linea);
            }
        }
    }

    private String numero() {
        String comprobante = venta.getComprobante();
        int n = comprobante.indexOf("N° ");
        return n < 0 ? comprobante : comprobante.substring(n);
    }

    private void centrado(PDFont fuente, float tamanio, String texto) throws IOException {
        for (String linea : partir(fuente, tamanio, Objects.toString(texto, ""), util)) {
            agregar(new Renglon(linea, null, fuente, tamanio, Alineacion.CENTRO, Color.BLACK, false));
        }
    }

    private void izquierda(PDFont fuente, float tamanio, String texto) throws IOException {
        for (String linea : partir(fuente, tamanio, Objects.toString(texto, ""), util)) {
            agregar(new Renglon(linea, null, fuente, tamanio, Alineacion.IZQUIERDA, Color.BLACK, false));
        }
    }

    private void importe(PDFont fuente, float tamanio, String etiqueta, BigDecimal valor) {
        agregar(new Renglon(etiqueta, CurrencyUtils.format(valor), fuente, tamanio,
                Alineacion.IZQUIERDA, Color.BLACK, false));
    }

    private void separador() {
        agregar(new Renglon(null, null, normal, NORMAL, Alineacion.IZQUIERDA, Color.BLACK, true));
    }

    private void agregar(Renglon renglon) {
        renglones.add(renglon);
    }

    // ------------------------------------------------------------------
    // Dibujo
    // ------------------------------------------------------------------

    /** y es el borde superior del renglón. */
    private void dibujar(PDPageContentStream cs, Renglon r, float y) throws IOException {

        if (r.separador()) {
            float yLinea = y - r.alto() / 2;
            cs.setLineDashPattern(new float[]{2, 2}, 0);
            cs.setLineWidth(0.5f);
            cs.moveTo(MARGEN, yLinea);
            cs.lineTo(ancho - MARGEN, yLinea);
            cs.stroke();
            cs.setLineDashPattern(new float[]{}, 0);
            return;
        }

        float base = y - r.tamanio();
        cs.setNonStrokingColor(r.color());

        float anchoDerecha = 0;

        if (r.derecha() != null) {
            String derecha = limpiar(r.fuente(), r.derecha());
            anchoDerecha = ancho(r.fuente(), r.tamanio(), derecha);
            texto(cs, r.fuente(), r.tamanio(), ancho - MARGEN - anchoDerecha, base, derecha);
        }

        // El texto de la izquierda no pisa el importe de la derecha.
        String izquierda = recortar(r.fuente(), r.tamanio(), r.texto(),
                util - (anchoDerecha == 0 ? 0 : anchoDerecha + 6));

        float x = r.alineacion() == Alineacion.CENTRO
                ? (ancho - ancho(r.fuente(), r.tamanio(), izquierda)) / 2
                : MARGEN;

        texto(cs, r.fuente(), r.tamanio(), x, base, izquierda);
        cs.setNonStrokingColor(Color.BLACK);
    }

    private static void texto(PDPageContentStream cs, PDFont fuente, float tamanio, float x, float y, String valor)
            throws IOException {
        cs.beginText();
        cs.setFont(fuente, tamanio);
        cs.newLineAtOffset(x, y);
        cs.showText(valor);
        cs.endText();
    }

    private float[] tamanioLogo(PDImageXObject logo) {
        float escala = Math.min(Math.min(LOGO_ANCHO_MAX, util) / logo.getWidth(), LOGO_ALTO_MAX / logo.getHeight());
        return new float[]{logo.getWidth() * escala, logo.getHeight() * escala};
    }

    /** "ANULADA" en diagonal, repetido a lo largo del ticket. */
    private void marcaDeAgua(PDPageContentStream cs, float alto) throws IOException {

        PDExtendedGraphicsState transparente = new PDExtendedGraphicsState();
        transparente.setNonStrokingAlphaConstant(0.18f);

        float tamanio = ancho / 6;
        float largo = ancho(negrita, tamanio, ANULADA);
        double angulo = Math.toRadians(35);
        float paso = 200;

        cs.saveGraphicsState();
        cs.setGraphicsStateParameters(transparente);
        cs.setNonStrokingColor(ROJO);

        for (float cy = Math.min(alto / 2, paso / 2); cy < alto; cy += paso) {
            float x = (float) (ancho / 2 - Math.cos(angulo) * largo / 2);
            float y = (float) (cy - Math.sin(angulo) * largo / 2);
            cs.beginText();
            cs.setFont(negrita, tamanio);
            cs.setTextMatrix(Matrix.getRotateInstance(angulo, x, y));
            cs.showText(ANULADA);
            cs.endText();
        }

        cs.restoreGraphicsState();
    }
}
