package org.gestionComercio.enums;

/**
 * Tipo de comprobante de una venta. Cada tipo lleva su propia numeración
 * correlativa.
 *
 * <p>Las facturas y notas requieren autorización de ARCA (facturación
 * electrónica), que todavía no está implementada: en el punto de venta sólo
 * se ofrecen los tipos de {@link #delPuntoDeVenta()}.
 */
public enum TipoComprobante implements Descriptible {

    FACTURA_A("Factura A"),
    FACTURA_B("Factura B"),
    FACTURA_C("Factura C"),
    TICKET("Ticket"),
    REMITO("Remito"),
    NOTA_CREDITO("Nota de crédito"),
    NOTA_DEBITO("Nota de débito");

    /** Dígitos con que se muestra el número del comprobante. */
    public static final int DIGITOS_NUMERO = 8;

    private final String descripcion;

    TipoComprobante(String descripcion) {
        this.descripcion = descripcion;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    /** Tipos que se pueden emitir desde el punto de venta (no requieren ARCA). */
    public static TipoComprobante[] delPuntoDeVenta() {
        return new TipoComprobante[]{TICKET, REMITO};
    }

    /** @return true si el tipo se puede emitir desde el punto de venta. */
    public boolean esDelPuntoDeVenta() {
        return this == TICKET || this == REMITO;
    }

    /** "Ticket N° 00000012"; vacío si falta el tipo o el número. */
    public static String formatear(TipoComprobante tipo, Long numero) {

        if (tipo == null || numero == null) {
            return "";
        }

        return tipo.getDescripcion() + " N° " + String.format("%0" + DIGITOS_NUMERO + "d", numero);
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
