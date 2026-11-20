package org.gestionComercio.enums;

/** Ancho del rollo de la impresora térmica donde se imprimen los tickets. */
public enum AnchoTicket implements Descriptible {

    MM_58(58, "58 mm"),
    MM_80(80, "80 mm");

    private final int milimetros;
    private final String descripcion;

    AnchoTicket(int milimetros, String descripcion) {
        this.milimetros = milimetros;
        this.descripcion = descripcion;
    }

    public int getMilimetros() {
        return milimetros;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    /** El ancho por defecto (el rollo más común). */
    public static AnchoTicket porDefecto() {
        return MM_80;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
