package org.gestionComercio.report;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import java.awt.Color;
import java.io.IOException;
import java.util.Map;

/**
 * Dibuja un código QR como cuadrados vectoriales: se imprime nítido en
 * cualquier resolución, también en impresoras térmicas.
 */
final class QrPdf {

    private QrPdf() {
    }

    /**
     * @param x    borde izquierdo
     * @param y    borde inferior
     * @param lado ancho y alto del código (incluido el margen blanco)
     */
    static void dibujar(PDPageContentStream cs, String texto, float x, float y, float lado) throws IOException {

        BitMatrix matriz;

        try {
            matriz = new QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
        } catch (WriterException e) {
            throw new IOException("No se pudo generar el código QR.", e);
        }

        int modulos = matriz.getWidth();
        float modulo = lado / modulos;

        cs.setNonStrokingColor(Color.BLACK);

        for (int fila = 0; fila < modulos; fila++) {
            for (int columna = 0; columna < modulos; columna++) {
                if (matriz.get(columna, fila)) {
                    cs.addRect(x + columna * modulo, y + lado - (fila + 1) * modulo, modulo, modulo);
                }
            }
        }

        cs.fill();
    }

}
