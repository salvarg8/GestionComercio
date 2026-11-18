package org.gestionComercio.enums;

/**
 * Qué generó un movimiento de inventario. Los movimientos de un comprobante
 * (venta o compra) guardan además el ID del comprobante, para poder rastrearlos
 * y revertirlos exactamente al anularlo.
 */
public enum OrigenMovimientoInventario implements Descriptible {

    /** Registrado a mano desde la pantalla de inventario. No tiene comprobante. */
    MANUAL("Manual"),

    VENTA("Venta"),

    COMPRA("Compra");

    private final String descripcion;

    OrigenMovimientoInventario(String descripcion) {
        this.descripcion = descripcion;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    /** @return true si el movimiento pertenece a un comprobante y debe indicar su ID. */
    public boolean requiereComprobante() {
        return this != MANUAL;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
