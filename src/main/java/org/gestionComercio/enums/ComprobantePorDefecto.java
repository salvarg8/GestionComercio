package org.gestionComercio.enums;

/** Comprobante que el punto de venta propone al empezar cada venta. */
public enum ComprobantePorDefecto implements Descriptible {

    /** La factura que corresponde al cliente; si la facturación electrónica no está habilitada, ticket. */
    FACTURA("Factura electrónica (si está habilitada; si no, ticket)"),
    TICKET("Ticket"),
    REMITO("Remito");

    private final String descripcion;

    ComprobantePorDefecto(String descripcion) {
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
