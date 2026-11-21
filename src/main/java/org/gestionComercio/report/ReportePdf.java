package org.gestionComercio.report;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.gestionComercio.dto.reporte.ColumnaReporte;
import org.gestionComercio.dto.reporte.Reporte;
import org.gestionComercio.util.DateUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Un {@link Reporte} en PDF: encabezado con la empresa, el título y el
 * período; la tabla, que continúa en las páginas siguientes repitiendo los
 * títulos de las columnas; la fila de totales y el número de página.
 *
 * <p>Cada columna toma el ancho de su texto más largo. Va en A4 vertical, o
 * apaisado si el contenido no entra; si tampoco así entra, se achican las
 * columnas de texto.
 * Un texto que no entra en su columna se corta con "...".
 */
public final class ReportePdf {

    private static final float MARGEN = 36;
    private static final float ALTO_FILA = 16;
    private static final float ALTO_PIE = 30;
    private static final float RELLENO = 4;
    private static final float TAMANIO = 8.5f;

    /** Lo menos que se achica una columna de texto cuando el reporte no entra en la página. */
    private static final float ANCHO_MINIMO = 40;

    private static final Color GRIS = new Color(110, 110, 110);
    private static final Color GRIS_CLARO = new Color(230, 230, 230);
    private static final Color GRIS_FILA = new Color(246, 246, 246);

    private final Reporte reporte;
    private final PDRectangle pagina;
    private final float[] colX;
    private final float[] colAncho;

    private final PDFont normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private PDDocument documento;
    private PDPageContentStream contenido;
    private float y;

    private ReportePdf(Reporte reporte) throws IOException {

        this.reporte = Objects.requireNonNull(reporte, "reporte");

        List<ColumnaReporte> columnas = reporte.columnas();
        int cantidad = columnas.size();

        float[] necesario = new float[cantidad];
        boolean[] ajustable = new boolean[cantidad];
        float total = 0;

        for (int i = 0; i < cantidad; i++) {
            necesario[i] = anchoNecesario(i);
            ajustable[i] = columnas.get(i).esAjustable();
            total += necesario[i];
        }

        // Apaisado sólo si el contenido no entra en vertical.
        this.pagina = total > PDRectangle.A4.getWidth() - 2 * MARGEN
                ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                : PDRectangle.A4;

        colAncho = ajustarAnchos(necesario, ajustable, pagina.getWidth() - 2 * MARGEN);
        colX = new float[cantidad];

        float x = MARGEN;

        for (int i = 0; i < cantidad; i++) {
            colX[i] = x;
            x += colAncho[i];
        }
    }

    /** Ancho que ocupa el texto más largo de la columna: título, datos o total. */
    private float anchoNecesario(int columna) throws IOException {

        ColumnaReporte definicion = reporte.columnas().get(columna);

        float ancho = Math.max(
                ancho(ComprobanteVentaPdf.limpiar(negrita, definicion.titulo()), negrita, TAMANIO),
                ancho(ComprobanteVentaPdf.limpiar(normal, reporte.textoMasLargo(columna)), normal, TAMANIO));

        if (reporte.tieneTotales()) {
            String total = definicion.formato().formatear(reporte.totales().get(columna));
            ancho = Math.max(ancho, ancho(ComprobanteVentaPdf.limpiar(negrita, total), negrita, TAMANIO));
        }

        return ancho + 2 * RELLENO + 1;
    }

    /**
     * Reparte el ancho disponible entre las columnas a partir de lo que necesita
     * cada una. Las numéricas y de fecha conservan su ancho; si sobra lugar se lo
     * llevan las de texto (en proporción a su contenido) y si falta se achican
     * primero las de texto más largas, que se cortan con "...". Sólo si ni así
     * entra, se achican todas en proporción.
     *
     * @return el ancho de cada columna; suman exactamente {@code disponible}
     */
    static float[] ajustarAnchos(float[] necesario, boolean[] ajustable, float disponible) {

        int cantidad = necesario.length;
        float[] anchos = necesario.clone();

        float total = 0;
        float fijo = 0;
        float ajustableTotal = 0;
        int ajustables = 0;

        for (int i = 0; i < cantidad; i++) {
            total += necesario[i];

            if (ajustable[i]) {
                ajustableTotal += necesario[i];
                ajustables++;
            } else {
                fijo += necesario[i];
            }
        }

        if (cantidad == 0) {
            return anchos;
        }

        if (total <= disponible) {

            float sobrante = disponible - total;

            for (int i = 0; i < cantidad; i++) {
                anchos[i] += ajustables == 0
                        ? sobrante * necesario[i] / total
                        : ajustable[i] ? sobrante * necesario[i] / ajustableTotal : 0;
            }

            return anchos;
        }

        if (fijo + ajustables * ANCHO_MINIMO > disponible) {

            for (int i = 0; i < cantidad; i++) {
                anchos[i] = necesario[i] * disponible / total;
            }

            return anchos;
        }

        // Tope común para las de texto: las que necesitan menos lo conservan y las demás quedan en el tope.
        float[] ordenados = new float[ajustables];
        int j = 0;

        for (int i = 0; i < cantidad; i++) {
            if (ajustable[i]) {
                ordenados[j++] = necesario[i];
            }
        }

        Arrays.sort(ordenados);

        float restante = disponible - fijo;
        int pendientes = ajustables;
        float tope = restante / pendientes;

        for (float valor : ordenados) {

            if (valor > tope) {
                break;
            }

            restante -= valor;
            pendientes--;
            tope = pendientes == 0 ? valor : restante / pendientes;
        }

        for (int i = 0; i < cantidad; i++) {
            if (ajustable[i]) {
                anchos[i] = Math.min(necesario[i], tope);
            }
        }

        return anchos;
    }

    /** @return el PDF del reporte */
    public static byte[] generar(Reporte reporte) {

        try {
            return new ReportePdf(reporte).generar();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el reporte en PDF.", e);
        }
    }

    private byte[] generar() throws IOException {

        try (PDDocument doc = new PDDocument()) {

            documento = doc;
            documento.getDocumentInformation().setTitle(reporte.titulo());
            documento.getDocumentInformation().setAuthor(reporte.empresa());

            nuevaPagina();
            encabezado();
            titulosDeColumnas();

            if (reporte.filas().isEmpty()) {
                escribir("No hay datos para el período seleccionado.", MARGEN, y - 12, normal, TAMANIO, GRIS);
                y -= ALTO_FILA;
            }

            for (int i = 0; i < reporte.filas().size(); i++) {
                saltoSiNoEntra();
                fila(reporte.filas().get(i), normal, i % 2 == 1);
            }

            if (reporte.tieneTotales()) {
                saltoSiNoEntra();
                linea(y);
                fila(reporte.totales(), negrita, false);
            }

            contenido.close();

            numerarPaginas();

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // Secciones
    // ------------------------------------------------------------------

    private void encabezado() throws IOException {

        if (reporte.empresa() != null && !reporte.empresa().isBlank()) {
            escribir(reporte.empresa(), MARGEN, y - 10, normal, 9, GRIS);
            y -= 14;
        }

        escribir(reporte.titulo(), MARGEN, y - 16, negrita, 16, Color.BLACK);
        y -= 22;

        escribir(reporte.filtros(), MARGEN, y - 10, normal, 10, Color.BLACK);

        String generado = "Generado el " + DateUtils.format(reporte.generado());
        escribirDerecha(generado, pagina.getWidth() - MARGEN, y - 10, normal, 9, GRIS);

        y -= 22;
    }

    private void titulosDeColumnas() throws IOException {

        rectangulo(MARGEN, y - ALTO_FILA, pagina.getWidth() - 2 * MARGEN, ALTO_FILA, GRIS_CLARO);

        for (int i = 0; i < colX.length; i++) {
            ColumnaReporte columna = reporte.columnas().get(i);
            celda(columna.titulo(), i, columna.formato().esNumerico(), negrita);
        }

        y -= ALTO_FILA;
    }

    private void fila(List<Object> celdas, PDFont fuente, boolean sombreada) throws IOException {

        if (sombreada) {
            rectangulo(MARGEN, y - ALTO_FILA, pagina.getWidth() - 2 * MARGEN, ALTO_FILA, GRIS_FILA);
        }

        for (int i = 0; i < colX.length; i++) {
            ColumnaReporte columna = reporte.columnas().get(i);
            Object valor = celdas.get(i);
            boolean derecha = columna.formato().esNumerico() && !(valor instanceof String);
            celda(columna.formato().formatear(valor), i, derecha, fuente);
        }

        y -= ALTO_FILA;
    }

    private void celda(String texto, int columna, boolean derecha, PDFont fuente) throws IOException {

        float ancho = colAncho[columna] - 2 * RELLENO;
        String ajustado = ajustar(texto, fuente, ancho);
        float base = y - ALTO_FILA + 5;

        if (derecha) {
            escribirDerecha(ajustado, colX[columna] + colAncho[columna] - RELLENO, base, fuente, TAMANIO, Color.BLACK);
        } else {
            escribir(ajustado, colX[columna] + RELLENO, base, fuente, TAMANIO, Color.BLACK);
        }
    }

    /** Pasa a una página nueva (con los títulos de las columnas) si la fila no entra. */
    private void saltoSiNoEntra() throws IOException {

        if (y - ALTO_FILA < MARGEN + ALTO_PIE) {
            contenido.close();
            nuevaPagina();
            titulosDeColumnas();
        }
    }

    private void numerarPaginas() throws IOException {

        int total = documento.getNumberOfPages();

        for (int i = 0; i < total; i++) {

            PDPage page = documento.getPage(i);

            try (PDPageContentStream pie = new PDPageContentStream(documento, page, AppendMode.APPEND, true, true)) {
                contenido = pie;
                escribir(reporte.titulo(), MARGEN, MARGEN, normal, 8, GRIS);
                escribirDerecha("Página " + (i + 1) + " de " + total,
                        pagina.getWidth() - MARGEN, MARGEN, normal, 8, GRIS);
            }
        }
    }

    // ------------------------------------------------------------------
    // Dibujo
    // ------------------------------------------------------------------

    private void nuevaPagina() throws IOException {

        PDPage page = new PDPage(pagina);
        documento.addPage(page);
        contenido = new PDPageContentStream(documento, page);
        y = pagina.getHeight() - MARGEN;
    }

    private void escribir(String texto, float x, float yBase, PDFont fuente, float tamanio, Color color)
            throws IOException {

        if (texto == null || texto.isEmpty()) {
            return;
        }

        contenido.beginText();
        contenido.setFont(fuente, tamanio);
        contenido.setNonStrokingColor(color);
        contenido.newLineAtOffset(x, yBase);
        contenido.showText(ComprobanteVentaPdf.limpiar(fuente, texto));
        contenido.endText();
    }

    private void escribirDerecha(String texto, float xDerecha, float yBase, PDFont fuente, float tamanio,
                                 Color color) throws IOException {

        if (texto == null || texto.isEmpty()) {
            return;
        }

        escribir(texto, xDerecha - ancho(ComprobanteVentaPdf.limpiar(fuente, texto), fuente, tamanio), yBase, fuente, tamanio, color);
    }

    private void rectangulo(float x, float yInferior, float ancho, float alto, Color color) throws IOException {

        contenido.setNonStrokingColor(color);
        contenido.addRect(x, yInferior, ancho, alto);
        contenido.fill();
    }

    private void linea(float yLinea) throws IOException {

        contenido.setStrokingColor(GRIS);
        contenido.setLineWidth(0.8f);
        contenido.moveTo(MARGEN, yLinea);
        contenido.lineTo(pagina.getWidth() - MARGEN, yLinea);
        contenido.stroke();
    }

    /** Corta el texto con "..." para que entre en el ancho indicado. */
    private String ajustar(String texto, PDFont fuente, float anchoMaximo) throws IOException {

        String limpio = ComprobanteVentaPdf.limpiar(fuente, texto);

        if (ancho(limpio, fuente, TAMANIO) <= anchoMaximo) {
            return limpio;
        }

        String puntos = "...";
        int fin = limpio.length();

        while (fin > 0 && ancho(limpio.substring(0, fin) + puntos, fuente, TAMANIO) > anchoMaximo) {
            fin--;
        }

        return limpio.substring(0, fin).stripTrailing() + puntos;
    }

    private static float ancho(String texto, PDFont fuente, float tamanio) throws IOException {
        return fuente.getStringWidth(texto) / 1000 * tamanio;
    }
}
