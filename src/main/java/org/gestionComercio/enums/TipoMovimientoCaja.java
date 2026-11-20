package org.gestionComercio.enums;

/**
 * Tipos de movimiento de caja. Los importes se guardan siempre positivos: el
 * tipo indica si el movimiento suma o resta al saldo ({@link #getSigno()}).
 *
 * <p>APERTURA registra el saldo inicial y CIERRE queda reservado; ninguno de
 * los dos cuenta como ingreso o egreso al calcular el saldo esperado.
 */
public enum TipoMovimientoCaja implements Descriptible {

    APERTURA("Apertura", 0, false),
    VENTA("Venta", 1, false),
    COBRO("Cobro", 1, false),
    /** Devolución del efectivo de una venta anulada. */
    ANULACION_VENTA("Anulación de venta", -1, false),
    /** Pago en efectivo de una compra. */
    PAGO_PROVEEDOR("Pago a proveedor", -1, false),
    /** Reintegro del efectivo pagado por una compra anulada. */
    ANULACION_COMPRA("Anulación de compra", 1, false),
    GASTO("Gasto", -1, true),
    RETIRO("Retiro", -1, true),
    INGRESO_MANUAL("Ingreso manual", 1, true),
    EGRESO_MANUAL("Egreso manual", -1, true),
    CIERRE("Cierre", 0, false);

    private final String descripcion;
    private final int signo;
    private final boolean manual;

    TipoMovimientoCaja(String descripcion, int signo, boolean manual) {
        this.descripcion = descripcion;
        this.signo = signo;
        this.manual = manual;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    /** @return 1 si suma al saldo, -1 si resta, 0 si no lo afecta. */
    public int getSigno() {
        return signo;
    }

    public boolean esIngreso() {
        return signo > 0;
    }

    public boolean esEgreso() {
        return signo < 0;
    }

    /** @return true si el usuario puede registrarlo a mano desde la pantalla de caja. */
    public boolean esManual() {
        return manual;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
