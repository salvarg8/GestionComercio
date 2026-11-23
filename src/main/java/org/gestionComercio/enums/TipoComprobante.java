package org.gestionComercio.enums;

/**
 * Tipo de comprobante de una venta.
 *
 * <p>El ticket y el remito son internos: cada tipo lleva su propia numeración
 * correlativa ({@code NumeradorComprobanteRepository}).
 *
 * <p>Las facturas y notas de crédito son electrónicas: las autoriza ARCA
 * (WSFEv1), que les da el número dentro del punto de venta y el CAE. Las
 * notas de crédito no se registran como ventas: se emiten al anular una
 * venta con factura autorizada.
 */
public enum TipoComprobante implements Descriptible {

    FACTURA_A("Factura A", 1, 'A'),
    FACTURA_B("Factura B", 6, 'B'),
    FACTURA_C("Factura C", 11, 'C'),
    TICKET("Ticket", null, null),
    REMITO("Remito", null, null),
    NOTA_CREDITO_A("Nota de crédito A", 3, 'A'),
    NOTA_CREDITO_B("Nota de crédito B", 8, 'B'),
    NOTA_CREDITO_C("Nota de crédito C", 13, 'C');

    /** Dígitos con que se muestra el número del comprobante. */
    public static final int DIGITOS_NUMERO = 8;

    /** Dígitos con que se muestra el punto de venta. */
    public static final int DIGITOS_PUNTO_VENTA = 4;

    private final String descripcion;

    /** Código de ARCA (FEParamGetTiposCbte); null si no es electrónico. */
    private final Integer codigoArca;

    /** Letra del comprobante electrónico; null si no es electrónico. */
    private final Character letra;

    TipoComprobante(String descripcion, Integer codigoArca, Character letra) {
        this.descripcion = descripcion;
        this.codigoArca = codigoArca;
        this.letra = letra;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    public Integer getCodigoArca() {
        return codigoArca;
    }

    public Character getLetra() {
        return letra;
    }

    /** Tipos que se pueden emitir desde el punto de venta sin facturación electrónica. */
    public static TipoComprobante[] delPuntoDeVenta() {
        return new TipoComprobante[]{TICKET, REMITO};
    }

    /** @return true si el tipo se puede emitir desde el punto de venta sin ARCA. */
    public boolean esDelPuntoDeVenta() {
        return this == TICKET || this == REMITO;
    }

    /** @return true si lo autoriza ARCA (facturas y notas de crédito). */
    public boolean esElectronico() {
        return codigoArca != null;
    }

    public boolean esFactura() {
        return this == FACTURA_A || this == FACTURA_B || this == FACTURA_C;
    }

    public boolean esNotaCredito() {
        return this == NOTA_CREDITO_A || this == NOTA_CREDITO_B || this == NOTA_CREDITO_C;
    }

    /** @return true si se puede registrar como venta: los tipos internos y las facturas. */
    public boolean seRegistraComoVenta() {
        return esDelPuntoDeVenta() || esFactura();
    }

    /** La factura de la letra indicada (A, B o C). */
    public static TipoComprobante factura(char letra) {
        return switch (letra) {
            case 'A' -> FACTURA_A;
            case 'B' -> FACTURA_B;
            case 'C' -> FACTURA_C;
            default -> throw new IllegalArgumentException("Letra de factura desconocida: " + letra);
        };
    }

    /** La nota de crédito de la misma letra que esta factura. */
    public TipoComprobante notaDeCredito() {
        return switch (this) {
            case FACTURA_A -> NOTA_CREDITO_A;
            case FACTURA_B -> NOTA_CREDITO_B;
            case FACTURA_C -> NOTA_CREDITO_C;
            default -> throw new IllegalStateException(descripcion + " no tiene nota de crédito.");
        };
    }

    /** "Ticket N° 00000012"; vacío si falta el tipo o el número. */
    public static String formatear(TipoComprobante tipo, Long numero) {

        if (tipo == null || numero == null) {
            return "";
        }

        return tipo.getDescripcion() + " N° " + String.format("%0" + DIGITOS_NUMERO + "d", numero);
    }

    /**
     * "Factura B N° 0003-00000123"; sin punto de venta, como
     * {@link #formatear(TipoComprobante, Long)}; vacío si falta el tipo o el número.
     */
    public static String formatear(TipoComprobante tipo, Integer puntoVenta, Long numero) {

        if (puntoVenta == null) {
            return formatear(tipo, numero);
        }

        if (tipo == null || numero == null) {
            return "";
        }

        return tipo.getDescripcion() + " N° "
                + String.format("%0" + DIGITOS_PUNTO_VENTA + "d", puntoVenta) + "-"
                + String.format("%0" + DIGITOS_NUMERO + "d", numero);
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
