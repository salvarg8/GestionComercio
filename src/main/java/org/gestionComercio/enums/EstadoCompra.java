package org.gestionComercio.enums;

public enum EstadoCompra implements Descriptible {

    /** Registrada pero todavía no recibida: no movió stock. */
    PENDIENTE("Pendiente"),

    /** Mercadería recibida: su stock ya se sumó. */
    RECIBIDA("Recibida"),

    CANCELADA("Cancelada"),

    /**
     * Anulada con {@code CompraService.anular}: si estaba recibida se descontó
     * su stock y, si se pagó en efectivo, se devolvió el importe a la caja.
     * Sigue en el listado.
     */
    ANULADA("Anulada");

    private final String descripcion;

    EstadoCompra(String descripcion) {
        this.descripcion = descripcion;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
