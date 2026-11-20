package org.gestionComercio.enums;

public enum FormaPago implements Descriptible {

    EFECTIVO("Efectivo"),
    TARJETA_DEBITO("Tarjeta de débito"),
    TARJETA_CREDITO("Tarjeta de crédito"),
    TRANSFERENCIA("Transferencia"),
    MERCADO_PAGO("Mercado Pago"),
    CUENTA_CORRIENTE("Cuenta corriente");

    private final String descripcion;

    FormaPago(String descripcion) {
        this.descripcion = descripcion;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    /** Formas de pago que se ofrecen en el punto de venta (cuenta corriente todavía no). */
    public static FormaPago[] delPuntoDeVenta() {
        return new FormaPago[]{EFECTIVO, TARJETA_DEBITO, TARJETA_CREDITO, TRANSFERENCIA, MERCADO_PAGO};
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
