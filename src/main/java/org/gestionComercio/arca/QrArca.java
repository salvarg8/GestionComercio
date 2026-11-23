package org.gestionComercio.arca;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Código QR obligatorio de los comprobantes electrónicos (RG 4892): una URL
 * de ARCA con los datos del comprobante en JSON codificado en base64.
 * Escaneado con el celular, muestra el comprobante en el sitio de ARCA.
 */
public final class QrArca {

    static final String URL = "https://www.afip.gob.ar/fe/qr/?p=";

    /** Tipo de código de autorización: CAE. */
    static final String TIPO_CODIGO_CAE = "E";

    private QrArca() {
    }

    /**
     * @param docTipo receptor; con 99 (sin identificar) o número 0 no se informa
     */
    public record Datos(
            LocalDate fecha,
            long cuitEmisor,
            int puntoVenta,
            int tipoComprobante,
            long numero,
            BigDecimal importeTotal,
            int docTipo,
            long docNro,
            String cae
    ) {
    }

    public static String url(Datos d) {
        return URL + Base64.getEncoder().encodeToString(json(d).getBytes(StandardCharsets.UTF_8));
    }

    /** JSON de la especificación v1, con los campos en su orden y sin espacios. */
    static String json(Datos d) {

        StringBuilder json = new StringBuilder("{")
                .append("\"ver\":1")
                .append(",\"fecha\":\"").append(d.fecha()).append('"')
                .append(",\"cuit\":").append(d.cuitEmisor())
                .append(",\"ptoVta\":").append(d.puntoVenta())
                .append(",\"tipoCmp\":").append(d.tipoComprobante())
                .append(",\"nroCmp\":").append(d.numero())
                .append(",\"importe\":").append(numero(d.importeTotal()))
                .append(",\"moneda\":\"").append(Wsfe.MONEDA_PESOS).append('"')
                .append(",\"ctz\":1");

        if (d.docNro() > 0 && d.docTipo() != org.gestionComercio.enums.TipoDocumento.CODIGO_ARCA_SIN_IDENTIFICAR) {
            json.append(",\"tipoDocRec\":").append(d.docTipo())
                    .append(",\"nroDocRec\":").append(d.docNro());
        }

        return json.append(",\"tipoCodAut\":\"").append(TIPO_CODIGO_CAE).append('"')
                .append(",\"codAut\":").append(d.cae())
                .append('}')
                .toString();
    }

    private static String numero(BigDecimal importe) {
        BigDecimal valor = importe == null ? BigDecimal.ZERO : importe.stripTrailingZeros();
        return (valor.scale() < 0 ? valor.setScale(0) : valor).toPlainString();
    }

}
