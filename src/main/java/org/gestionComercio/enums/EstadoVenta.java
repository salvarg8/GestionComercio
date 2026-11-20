package org.gestionComercio.enums;

public enum EstadoVenta implements Descriptible {

    PENDIENTE("Pendiente"),
    PAGADA("Pagada"),
    CANCELADA("Cancelada");

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
