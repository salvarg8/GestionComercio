package org.gestionComercio.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.dto.arca.IvaComprobanteArcaDto;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.CuitUtils;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.DateUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Comprobante de una venta en PDF. El ticket se arma en el rollo de la
 * impresora térmica ({@link TicketPdf}) o en A4, según la configuración; los
 * demás comprobantes, en A4: datos de la empresa, del cliente, renglones,
 * totales y el texto de pie configurado. Una venta anulada se marca como ANULADA en el
 * encabezado y con una marca de agua en cada página.
 *
 * <p>Los remitos (y los tickets) no son comprobantes fiscales, y así lo indica
 * el pie de cada página. Las facturas electrónicas llevan la letra y el código
 * del comprobante, el IVA discriminado (A) o contenido (B), el CAE con su
 * vencimiento y el código QR de ARCA; mientras ARCA no las autorice, salen
 * marcadas como no válidas.
 */
public final class ComprobanteVentaPdf {

    static final String LEYENDA_NO_FISCAL = "Documento no válido como factura.";

    static final String LEYENDA_SIN_CAE = "Pendiente de autorización de ARCA: documento no válido como factura.";

    static final String LEYENDA_TRANSPARENCIA = "Régimen de Transparencia Fiscal al Consumidor (Ley 27.743)";

    static final String ANULADA = "ANULADA";

    private static final float LADO_QR = 84;
    private static final float LADO_LETRA = 38;

    private static final PDRectangle PAGINA = PDRectangle.A4;
    private static final float MARGEN = 40;
    private static final float ANCHO_UTIL = PAGINA.getWidth() - 2 * MARGEN;
    private static final float ALTO_RENGLON = 16;
    private static final float LOGO_MAX = 64;

    /** Espacio que se reserva abajo para el pie de página. */
    private static final float ALTO_PIE = 40;

    private static final Color GRIS = new Color(110, 110, 110);
    private static final Color GRIS_CLARO = new Color(225, 225, 225);
    private static final Color ROJO = new Color(190, 30, 30);

    // Columnas de la tabla: x de inicio y ancho.
    private static final float[] COL_X = {MARGEN, MARGEN + 80, MARGEN + 300, MARGEN + 370, MARGEN + 445};
    private static final float[] COL_ANCHO = {75, 215, 65, 70, ANCHO_UTIL - 445};
    private static final String[] COL_TITULO = {"Código", "Descripción", "Cantidad", "P. unitario", "Subtotal"};

    private final VentaDto venta;
    private final EmpresaDto empresa;
    private final ClienteDto cliente;

    /** Factura electrónica; null en remitos. */
    private final ComprobanteArcaDto factura;

    private final PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private PDDocument documento;
    private PDPageContentStream contenido;
    private float y;

    private final OpcionesComprobante opciones;

    private ComprobanteVentaPdf(VentaDto venta, EmpresaDto empresa, ClienteDto cliente,
                                OpcionesComprobante opciones) {
        this.venta = Objects.requireNonNull(venta, "venta");
        this.empresa = Objects.requireNonNull(empresa, "empresa");
        this.cliente = cliente;
        this.factura = venta.getFactura();
        this.opciones = Objects.requireNonNullElse(opciones, OpcionesComprobante.porDefecto());
    }

    /**
     * Con el formato por defecto (ticket de 80 mm, con logo y sin pie).
     *
     * @see #generar(VentaDto, EmpresaDto, ClienteDto, OpcionesComprobante)
     */
    public static byte[] generar(VentaDto venta, EmpresaDto empresa, ClienteDto cliente) {
        return generar(venta, empresa, cliente, OpcionesComprobante.porDefecto());
    }

    /**
     * @param venta    la venta con sus renglones
     * @param empresa  datos de la empresa (Configuración)
     * @param cliente  null para consumidor final
     * @param opciones formato del ticket, logo y texto de pie; null = por defecto
     * @return el PDF
     */
    public static byte[] generar(VentaDto venta, EmpresaDto empresa, ClienteDto cliente,
                                 OpcionesComprobante opciones) {

        OpcionesComprobante formato = Objects.requireNonNullElse(opciones, OpcionesComprobante.porDefecto());

        try {
            // El ticket va en rollo de impresora térmica salvo que se configure A4; el resto, en A4.
            if (venta != null && venta.getTipoComprobante() == TipoComprobante.TICKET
                    && formato.formatoTicket().esTermico()) {
                return new TicketPdf(venta, empresa, cliente, formato).generar();
            }

            return new ComprobanteVentaPdf(venta, empresa, cliente, formato).generar();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el comprobante en PDF.", e);
        }
    }

    private byte[] generar() throws IOException {

        try (PDDocument doc = new PDDocument()) {

            documento = doc;
            documento.getDocumentInformation().setTitle(venta.getComprobante());
            documento.getDocumentInformation().setAuthor(empresa.getRazonSocial());

            nuevaPagina();
            encabezado();
            datosVenta();
            renglones();
            totales();

            if (factura != null) {
                datosFiscales();
            }

            textoDePie();

            contenido.close();

            marcarPaginas();

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // Secciones
    // ------------------------------------------------------------------

    private void encabezado() throws IOException {

        float arriba = y;
        float xTexto = MARGEN;

        PDImageXObject logo = logo();

        if (logo != null) {
            float escala = Math.min(LOGO_MAX / logo.getWidth(), LOGO_MAX / logo.getHeight());
            float ancho = logo.getWidth() * escala;
            float alto = logo.getHeight() * escala;
            contenido.drawImage(logo, MARGEN, arriba - alto, ancho, alto);
            xTexto += ancho + 10;
        }

        // En las facturas, la letra va en un recuadro al centro: el texto de la izquierda se acorta.
        float limiteIzq = factura == null ? MARGEN + 290 : MARGEN + 245;

        // Izquierda: identificación del comercio.
        float yIzq = arriba - 12;
        texto(negrita, 13, xTexto, yIzq, recortar(negrita, 13, empresa.getRazonSocial(), limiteIzq - xTexto));
        yIzq -= 15;

        for (String linea : lineasNoVacias(
                empresa.getNombreFantasia(),
                empresa.getDireccion(),
                unir(" · ", prefijo("Tel. ", empresa.getTelefono()), empresa.getEmail()))) {
            texto(normal, 9, xTexto, yIzq, recortar(normal, 9, linea, limiteIzq - xTexto));
            yIzq -= 12;
        }

        if (factura != null) {
            letraDelComprobante(arriba);
        }

        // Derecha: comprobante y datos fiscales del comercio.
        float xDer = MARGEN + 310;
        float anchoDer = ANCHO_UTIL - 310;
        float yDer = arriba - 14;

        String tipo = venta.getTipoComprobante() == null ? "Comprobante" : venta.getTipoComprobante().getDescripcion();
        texto(negrita, 16, xDer, yDer, tipo.toUpperCase());
        yDer -= 16;
        texto(negrita, 11, xDer, yDer, numero());
        yDer -= 14;

        boolean conFechaFiscal = factura != null && factura.getFecha() != null;
        texto(normal, 9, xDer, yDer, conFechaFiscal
                ? "Fecha de emisión: " + DateUtils.format(factura.getFecha())
                : "Fecha: " + DateUtils.format(venta.getFecha()));
        yDer -= 14;

        for (String linea : lineasNoVacias(
                prefijo("CUIT: ", CuitUtils.formatear(empresa.getCuit())),
                prefijo("Condición IVA: ", empresa.getCondicionIva() == null
                        ? null : empresa.getCondicionIva().getDescripcion()),
                prefijo("Ingresos Brutos: ", empresa.getIngresosBrutos()),
                factura == null ? null
                        : prefijo("Inicio de actividades: ", DateUtils.format(empresa.getFechaInicioActividades())))) {
            texto(normal, 9, xDer, yDer, recortar(normal, 9, linea, anchoDer));
            yDer -= 12;
        }

        y = Math.min(Math.min(yIzq, yDer), logo == null ? arriba : arriba - LOGO_MAX) - 6;

        linea(y);
        y -= 16;
    }

    private void datosVenta() throws IOException {

        String nombreCliente = cliente == null
                ? Objects.toString(venta.getClienteNombre(), VentaDto.CONSUMIDOR_FINAL)
                : cliente.getNombreCompleto();

        par("Cliente:", nombreCliente, MARGEN, 300);
        par("Forma de pago:", Objects.toString(venta.getFormaPago(), ""), MARGEN + 310, ANCHO_UTIL - 310);
        y -= 13;

        if (cliente != null) {

            String documentoCliente = cliente.getNumeroDocumento() == null ? ""
                    : unir(" ", Objects.toString(cliente.getTipoDocumento(), ""), cliente.getDocumentoFormateado());

            par("Documento:", documentoCliente, MARGEN, 300);
            par("Condición IVA:", cliente.getCondicionIva() == null ? "" : cliente.getCondicionIva().getDescripcion(),
                    MARGEN + 310, ANCHO_UTIL - 310);
            y -= 13;

            if (cliente.getDireccion() != null && !cliente.getDireccion().isBlank()) {
                par("Dirección:", cliente.getDireccion(), MARGEN, ANCHO_UTIL);
                y -= 13;
            }
        }

        String vendedor = venta.getUsuarioNombreCompleto() == null || venta.getUsuarioNombreCompleto().isBlank()
                ? venta.getUsuarioNombre() : venta.getUsuarioNombreCompleto();
        par("Vendedor:", Objects.toString(vendedor, ""), MARGEN, 300);
        y -= 13;

        if (venta.getEstadoVenta() == EstadoVenta.ANULADA) {

            y -= 4;
            contenido.setNonStrokingColor(ROJO);
            texto(negrita, 12, MARGEN, y, ANULADA + " el " + DateUtils.format(venta.getFechaAnulacion())
                    + Objects.toString(prefijo(" por ", venta.getUsuarioAnulacionNombreCompleto()), ""));
            y -= 14;

            for (String linea : partir(normal, 9, "Motivo: " + Objects.toString(venta.getMotivoAnulacion(), ""),
                    ANCHO_UTIL)) {
                texto(normal, 9, MARGEN, y, linea);
                y -= 12;
            }

            ComprobanteArcaDto nota = venta.getNotaCredito();

            if (nota != null) {
                String detalle = nota.isAutorizado()
                        ? nota.getComprobante() + " — CAE " + nota.getCae()
                        : nota.getComprobante();
                texto(normal, 9, MARGEN, y, recortar(normal, 9, "Anulada con " + detalle, ANCHO_UTIL));
                y -= 12;
            }

            contenido.setNonStrokingColor(Color.BLACK);
        }

        y -= 8;
    }

    private void renglones() throws IOException {

        encabezadoTabla();

        List<DetalleVentaDto> detalles = Objects.requireNonNullElse(venta.getDetalles(), List.of());

        for (DetalleVentaDto d : detalles) {

            if (y < MARGEN + ALTO_PIE + ALTO_RENGLON) {
                contenido.close();
                nuevaPagina();
                encabezadoTabla();
            }

            celda(0, Objects.toString(d.getProductoCodigo(), ""), false);
            celda(1, Objects.toString(d.getProductoNombre(), ""), false);
            celda(2, CantidadUtils.formatear(d.getCantidad()), true);
            celda(3, CurrencyUtils.format(d.getPrecioUnitario()), true);
            celda(4, CurrencyUtils.format(d.getSubtotal()), true);

            y -= ALTO_RENGLON;
        }

        if (detalles.isEmpty()) {
            texto(normal, 9, MARGEN, y, "La venta no tiene renglones.");
            y -= ALTO_RENGLON;
        }

        linea(y + ALTO_RENGLON - 12);
        y -= 8;
    }

    private void totales() throws IOException {

        boolean discrimina = factura != null && factura.getTipoComprobante() == TipoComprobante.FACTURA_A
                && factura.getImporteNeto() != null;
        int renglonesIva = discrimina ? factura.getAlicuotas().size() + 2 : 0;

        // Subtotal, descuento, IVA y total van juntos.
        if (y < MARGEN + ALTO_PIE + (3 + renglonesIva) * ALTO_RENGLON) {
            contenido.close();
            nuevaPagina();
        }

        float xEtiqueta = COL_X[3];

        texto(normal, 10, xEtiqueta, y, "Subtotal:");
        derecha(normal, 10, venta.getSubtotal());
        y -= 14;

        texto(normal, 10, xEtiqueta, y, "Descuento:");
        derecha(normal, 10, venta.getDescuento());
        y -= 14;

        // Factura A: neto gravado, IVA por alícuota y exento (con el descuento ya aplicado).
        if (discrimina) {

            texto(normal, 10, xEtiqueta - 40, y, "Neto gravado:");
            derecha(normal, 10, factura.getImporteNeto());
            y -= 14;

            for (IvaComprobanteArcaDto iva : factura.getAlicuotas()) {
                texto(normal, 10, xEtiqueta - 40, y, iva.getDescripcion() + ":");
                derecha(normal, 10, iva.importe());
                y -= 14;
            }

            texto(normal, 10, xEtiqueta - 40, y, "Exento:");
            derecha(normal, 10, factura.getImporteExento());
            y -= 14;
        }

        y -= 4;
        texto(negrita, 13, xEtiqueta, y, "TOTAL:");
        derecha(negrita, 13, venta.getTotal());
        y -= 14;

        // Factura B: IVA contenido en el precio, para el consumidor final.
        if (factura != null && factura.getTipoComprobante() == TipoComprobante.FACTURA_B
                && factura.getImporteIva() != null) {
            y -= 2;
            texto(normal, 8, xEtiqueta - 150, y, LEYENDA_TRANSPARENCIA);
            y -= 11;
            texto(normal, 9, xEtiqueta - 150, y, "IVA contenido:");
            derecha(normal, 9, factura.getImporteIva());
            y -= 12;
        }
    }

    /** CAE, vencimiento y código QR; o el aviso de que todavía no está autorizada. */
    private void datosFiscales() throws IOException {

        float alto = factura.isAutorizado() ? LADO_QR + 8 : 40;

        if (y < MARGEN + ALTO_PIE + alto) {
            contenido.close();
            nuevaPagina();
        }

        y -= 6;
        linea(y);
        y -= 6;

        if (!factura.isAutorizado()) {
            y -= 12;
            contenido.setNonStrokingColor(ROJO);
            texto(negrita, 11, MARGEN, y, LEYENDA_SIN_CAE);
            contenido.setNonStrokingColor(Color.BLACK);
            y -= 13;

            if (factura.getObservaciones() != null && !factura.getObservaciones().isBlank()) {
                texto(normal, 8, MARGEN, y, recortar(normal, 8,
                        factura.getEstado().getDescripcion() + ": " + factura.getObservaciones(), ANCHO_UTIL));
                y -= 11;
            }
            return;
        }

        float abajo = y - LADO_QR;
        QrPdf.dibujar(contenido, factura.getQrUrl(), MARGEN, abajo, LADO_QR);

        float xTexto = MARGEN + LADO_QR + 12;
        float yTexto = y - 22;

        texto(negrita, 11, xTexto, yTexto, "Comprobante autorizado por ARCA");
        yTexto -= 16;
        texto(normal, 10, xTexto, yTexto, "CAE N°: " + factura.getCae());
        yTexto -= 14;
        texto(normal, 10, xTexto, yTexto, "Vencimiento del CAE: " + DateUtils.format(factura.getVencimientoCae()));

        y = abajo - 4;
    }

    /** El texto de pie de la configuración (horario, redes, políticas de cambio), centrado. */
    private void textoDePie() throws IOException {

        List<String> lineas = new ArrayList<>();

        for (String linea : opciones.lineasPie()) {
            lineas.addAll(linea.isEmpty() ? List.of("") : partir(normal, 9, linea, ANCHO_UTIL));
        }

        if (lineas.isEmpty()) {
            return;
        }

        if (y < MARGEN + ALTO_PIE + 16 + lineas.size() * 12) {
            contenido.close();
            nuevaPagina();
        }

        y -= 8;
        linea(y);
        y -= 16;

        for (String linea : lineas) {
            texto(normal, 9, (PAGINA.getWidth() - ancho(normal, 9, linea)) / 2, y, linea);
            y -= 12;
        }
    }

    /** Recuadro con la letra (A, B, C) y el código de ARCA del comprobante, al centro del encabezado. */
    private void letraDelComprobante(float arriba) throws IOException {

        TipoComprobante tipo = factura.getTipoComprobante();
        float x = MARGEN + 262;

        contenido.setStrokingColor(Color.BLACK);
        contenido.setLineWidth(1.2f);
        contenido.addRect(x, arriba - LADO_LETRA, LADO_LETRA, LADO_LETRA);
        contenido.stroke();

        String letra = String.valueOf(tipo.getLetra());
        texto(negrita, 26, x + (LADO_LETRA - ancho(negrita, 26, letra)) / 2, arriba - LADO_LETRA + 10, letra);

        String codigo = "COD. " + String.format("%02d", tipo.getCodigoArca());
        texto(normal, 7, x + (LADO_LETRA - ancho(normal, 7, codigo)) / 2, arriba - LADO_LETRA - 9, codigo);
    }

    /** Pie con la leyenda y el número de página, y la marca de agua si está anulada. */
    private void marcarPaginas() throws IOException {

        int total = documento.getNumberOfPages();
        boolean anulada = venta.getEstadoVenta() == EstadoVenta.ANULADA;

        for (int i = 0; i < total; i++) {

            PDPage pagina = documento.getPage(i);

            try (PDPageContentStream cs = new PDPageContentStream(documento, pagina, AppendMode.APPEND, true, true)) {

                contenido = cs;

                cs.setNonStrokingColor(GRIS);
                texto(normal, 8, MARGEN, MARGEN - 10, limpiar(normal, venta.getComprobante() + " — "
                        + leyendaPie()));
                String pag = "Página " + (i + 1) + " de " + total;
                texto(normal, 8, PAGINA.getWidth() - MARGEN - ancho(normal, 8, pag), MARGEN - 10, pag);

                if (anulada) {
                    marcaDeAgua(cs);
                }
            }
        }
    }

    private String leyendaPie() {

        if (factura == null) {
            return LEYENDA_NO_FISCAL;
        }

        return factura.isAutorizado() ? "CAE " + factura.getCae() : LEYENDA_SIN_CAE;
    }

    private void marcaDeAgua(PDPageContentStream cs) throws IOException {

        PDExtendedGraphicsState transparente = new PDExtendedGraphicsState();
        transparente.setNonStrokingAlphaConstant(0.18f);

        float tamanio = 110;
        float largo = ancho(negrita, tamanio, ANULADA);
        double angulo = Math.toRadians(45);
        float cx = PAGINA.getWidth() / 2;
        float cy = PAGINA.getHeight() / 2;

        // Centra el texto girado sobre el centro de la página.
        float x = (float) (cx - Math.cos(angulo) * largo / 2 + Math.sin(angulo) * tamanio / 3);
        float yy = (float) (cy - Math.sin(angulo) * largo / 2 - Math.cos(angulo) * tamanio / 3);

        cs.saveGraphicsState();
        cs.setGraphicsStateParameters(transparente);
        cs.setNonStrokingColor(ROJO);
        cs.beginText();
        cs.setFont(negrita, tamanio);
        cs.setTextMatrix(Matrix.getRotateInstance(angulo, x, yy));
        cs.showText(ANULADA);
        cs.endText();
        cs.restoreGraphicsState();
    }

    // ------------------------------------------------------------------
    // Tabla
    // ------------------------------------------------------------------

    private void encabezadoTabla() throws IOException {

        contenido.setNonStrokingColor(GRIS_CLARO);
        contenido.addRect(MARGEN, y - 5, ANCHO_UTIL, ALTO_RENGLON);
        contenido.fill();
        contenido.setNonStrokingColor(Color.BLACK);

        for (int i = 0; i < COL_TITULO.length; i++) {
            if (i >= 2) {
                texto(negrita, 9, COL_X[i] + COL_ANCHO[i] - ancho(negrita, 9, COL_TITULO[i]), y, COL_TITULO[i]);
            } else {
                texto(negrita, 9, COL_X[i] + 2, y, COL_TITULO[i]);
            }
        }

        y -= ALTO_RENGLON + 2;
    }

    private void celda(int columna, String valor, boolean alineadaDerecha) throws IOException {

        String limpio = recortar(normal, 9, valor, COL_ANCHO[columna] - 4);
        float x = alineadaDerecha
                ? COL_X[columna] + COL_ANCHO[columna] - ancho(normal, 9, limpio)
                : COL_X[columna] + 2;

        texto(normal, 9, x, y, limpio);
    }

    private void derecha(PDFont fuente, float tamanio, BigDecimal importe) throws IOException {
        String valor = limpiar(fuente, CurrencyUtils.format(importe));
        texto(fuente, tamanio, PAGINA.getWidth() - MARGEN - ancho(fuente, tamanio, valor), y, valor);
    }

    // ------------------------------------------------------------------
    // Primitivas
    // ------------------------------------------------------------------

    private void nuevaPagina() throws IOException {
        PDPage pagina = new PDPage(PAGINA);
        documento.addPage(pagina);
        contenido = new PDPageContentStream(documento, pagina);
        y = PAGINA.getHeight() - MARGEN;
    }

    /** "Etiqueta: valor" con la etiqueta en negrita, recortado al ancho indicado. */
    private void par(String etiqueta, String valor, float x, float anchoMaximo) throws IOException {
        texto(negrita, 9, x, y, etiqueta);
        float desde = x + ancho(negrita, 9, etiqueta) + 4;
        texto(normal, 9, desde, y, recortar(normal, 9, valor, anchoMaximo - (desde - x)));
    }

    private void texto(PDFont fuente, float tamanio, float x, float yTexto, String valor) throws IOException {
        contenido.beginText();
        contenido.setFont(fuente, tamanio);
        contenido.newLineAtOffset(x, yTexto);
        contenido.showText(limpiar(fuente, valor));
        contenido.endText();
    }

    private void linea(float yLinea) throws IOException {
        contenido.setStrokingColor(GRIS);
        contenido.setLineWidth(0.6f);
        contenido.moveTo(MARGEN, yLinea);
        contenido.lineTo(PAGINA.getWidth() - MARGEN, yLinea);
        contenido.stroke();
        contenido.setStrokingColor(Color.BLACK);
    }

    private String numero() {
        String comprobante = venta.getComprobante();
        int n = comprobante.indexOf("N° ");
        return n < 0 ? comprobante : comprobante.substring(n);
    }

    private PDImageXObject logo() {
        return opciones.imprimirLogo() ? cargarLogo(empresa.getLogo(), documento) : null;
    }

    /** El logo de la empresa, o null si no tiene o no se puede leer (el comprobante sale igual). */
    static PDImageXObject cargarLogo(String ruta, PDDocument documento) {

        if (ruta == null || ruta.isBlank() || !Files.isRegularFile(Path.of(ruta))) {
            return null;
        }

        try {
            return PDImageXObject.createFromFile(ruta, documento);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Texto
    // ------------------------------------------------------------------

    static float ancho(PDFont fuente, float tamanio, String texto) throws IOException {
        return fuente.getStringWidth(limpiar(fuente, texto)) / 1000 * tamanio;
    }

    /**
     * Deja sólo caracteres que la fuente puede escribir (las estándar de PDF
     * usan WinAnsi: acentos y ñ sí, otros símbolos no). Los saltos de línea y
     * tabulaciones pasan a espacios y lo que no se puede escribir, a "?".
     */
    static String limpiar(PDFont fuente, String texto) {

        if (texto == null) {
            return "";
        }

        StringBuilder limpio = new StringBuilder(texto.length());

        texto.codePoints().forEach(cp -> {

            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                limpio.append(cp == ' ' || cp == ' ' ? ' ' : ' ');
                return;
            }

            if (Character.isISOControl(cp)) {
                return;
            }

            String caracter = new String(Character.toChars(cp));

            try {
                fuente.encode(caracter);
                limpio.append(caracter);
            } catch (IOException | IllegalArgumentException e) {
                limpio.append('?');
            }
        });

        return limpio.toString();
    }

    /** Recorta el texto con "…" para que no supere el ancho. */
    static String recortar(PDFont fuente, float tamanio, String texto, float anchoMaximo) throws IOException {

        String limpio = limpiar(fuente, texto);

        if (ancho(fuente, tamanio, limpio) <= anchoMaximo) {
            return limpio;
        }

        String puntos = "…";
        int fin = limpio.length();

        while (fin > 0 && ancho(fuente, tamanio, limpio.substring(0, fin) + puntos) > anchoMaximo) {
            fin--;
        }

        return limpio.substring(0, fin).stripTrailing() + puntos;
    }

    /** Parte el texto en renglones que entren en el ancho (corta por palabras). */
    static List<String> partir(PDFont fuente, float tamanio, String texto, float anchoMaximo) throws IOException {

        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();

        for (String palabra : limpiar(fuente, texto).split(" ")) {

            String candidata = actual.isEmpty() ? palabra : actual + " " + palabra;

            if (ancho(fuente, tamanio, candidata) <= anchoMaximo || actual.isEmpty()) {
                actual.setLength(0);
                actual.append(candidata);
            } else {
                lineas.add(recortar(fuente, tamanio, actual.toString(), anchoMaximo));
                actual.setLength(0);
                actual.append(palabra);
            }
        }

        if (!actual.isEmpty()) {
            lineas.add(recortar(fuente, tamanio, actual.toString(), anchoMaximo));
        }

        return lineas;
    }

    static List<String> lineasNoVacias(String... valores) {
        List<String> lineas = new ArrayList<>();
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                lineas.add(valor.trim());
            }
        }
        return lineas;
    }

    static String prefijo(String prefijo, String valor) {
        return valor == null || valor.isBlank() ? null : prefijo + valor.trim();
    }

    static String unir(String separador, String... valores) {
        String unido = String.join(separador, lineasNoVacias(valores));
        return unido.isEmpty() ? null : unido;
    }

}
