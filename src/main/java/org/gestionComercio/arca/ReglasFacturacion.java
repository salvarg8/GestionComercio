package org.gestionComercio.arca;

import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.util.CurrencyUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Qué factura corresponde según la condición frente al IVA del comercio y
 * del cliente, y cómo se identifica el receptor ante ARCA.
 *
 * <ul>
 *   <li>Responsable inscripto: factura A a responsables inscriptos y
 *       monotributistas; factura B al resto (consumidor final, exento, etc.).</li>
 *   <li>Monotributista o exento: siempre factura C.</li>
 * </ul>
 *
 * <p>Validar con un contador los casos particulares antes de pasar a producción.
 */
public final class ReglasFacturacion {

    private ReglasFacturacion() {
    }

    /** Receptor del comprobante: DocTipo, DocNro y CondicionIVAReceptorId. */
    public record Receptor(int docTipo, long docNro, CondicionIVA condicionIva) {

        public static final Receptor CONSUMIDOR_FINAL_SIN_IDENTIFICAR =
                new Receptor(TipoDocumento.CODIGO_ARCA_SIN_IDENTIFICAR, 0, CondicionIVA.CONSUMIDOR_FINAL);

        public boolean identificado() {
            return docTipo != TipoDocumento.CODIGO_ARCA_SIN_IDENTIFICAR && docNro > 0;
        }

        public boolean conCuit() {
            return docTipo == TipoDocumento.CUIT.getCodigoArca();
        }
    }

    /** La factura que le corresponde emitir al comercio para ese receptor. */
    public static TipoComprobante tipoFactura(CondicionIVA emisor, CondicionIVA receptor) {

        if (emisor == CondicionIVA.RESPONSABLE_INSCRIPTO) {
            return receptor != null && receptor.recibeFacturaA()
                    ? TipoComprobante.FACTURA_A
                    : TipoComprobante.FACTURA_B;
        }

        return TipoComprobante.FACTURA_C;
    }

    /**
     * El receptor ante ARCA de un cliente; sin cliente, consumidor final sin identificar.
     *
     * @param numeroDocumento sólo dígitos (si tiene otros caracteres, ARCA no lo acepta)
     */
    public static Receptor receptor(TipoDocumento tipoDocumento, String numeroDocumento, CondicionIVA condicion) {

        if (tipoDocumento == null || numeroDocumento == null || numeroDocumento.isBlank()) {
            return Receptor.CONSUMIDOR_FINAL_SIN_IDENTIFICAR;
        }

        String digitos = numeroDocumento.replaceAll("[\\s.\\-]", "");

        if (!digitos.matches("\\d{1,11}")) {
            throw new ArcaException("El documento del cliente (" + numeroDocumento + ") tiene caracteres que ARCA "
                    + "no acepta: sólo se admiten números.");
        }

        return new Receptor(tipoDocumento.getCodigoArca(), Long.parseLong(digitos),
                condicion == null ? CondicionIVA.CONSUMIDOR_FINAL : condicion);
    }

    /**
     * Motivos por los que no se puede emitir ese comprobante a ese receptor.
     *
     * @param montoIdentificacion desde este total, al consumidor final hay que identificarlo
     * @return vacío si se puede emitir
     */
    public static List<String> problemas(TipoComprobante tipo, CondicionIVA emisor, Receptor receptor,
                                         BigDecimal total, BigDecimal montoIdentificacion) {

        List<String> problemas = new ArrayList<>();

        TipoComprobante esperado = tipoFactura(emisor, receptor.condicionIva());

        if (tipo != esperado) {
            problemas.add("A un cliente " + receptor.condicionIva().getDescripcion().toLowerCase()
                    + " el comercio (" + emisor.getDescripcion().toLowerCase() + ") le emite "
                    + esperado.getDescripcion().toLowerCase() + ", no " + tipo.getDescripcion().toLowerCase() + ".");
        }

        if (tipo == TipoComprobante.FACTURA_A && !receptor.conCuit()) {
            problemas.add("La factura A requiere un cliente identificado con CUIT.");
        }

        if (!receptor.identificado() && total != null && montoIdentificacion != null
                && total.compareTo(montoIdentificacion) >= 0) {
            problemas.add("Las ventas a consumidor final desde " + CurrencyUtils.format(montoIdentificacion)
                    + " deben identificar al comprador: seleccione un cliente con documento.");
        }

        return problemas;
    }

}
