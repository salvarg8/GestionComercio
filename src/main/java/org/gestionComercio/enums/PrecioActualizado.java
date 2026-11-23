package org.gestionComercio.enums;

/** Precio que modifica una actualización masiva de precios. */
public enum PrecioActualizado implements Descriptible {

    VENTA("Precio de venta"),
    COMPRA("Precio de compra"),
    AMBOS("Precio de compra y de venta");

    private final String descripcion;

    PrecioActualizado(String descripcion) {
        this.descripcion = descripcion;
    }

    public boolean incluyeVenta() {
        return this != COMPRA;
    }

    public boolean incluyeCompra() {
        return this != VENTA;
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
