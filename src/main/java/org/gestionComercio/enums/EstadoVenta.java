package org.gestionComercio.enums;

public enum EstadoVenta implements Descriptible {

    PENDIENTE("Pendiente"),
    PAGADA("Pagada"),
    CANCELADA("Cancelada"),

    /**
     * Anulada con {@code VentaService.anular}: se devolvió su stock y, si se
     * cobró en efectivo, se registró el egreso en caja. Sigue en el listado.
     */
    ANULADA("Anulada");

    private final String descripcion;

    EstadoVenta(String descripcion) {
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
