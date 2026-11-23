package org.gestionComercio.enums;

/**
 * Formato en que se imprimen los tickets: en el rollo de una impresora
 * térmica (58 u 80 mm) o en una hoja A4. Las facturas y los remitos salen
 * siempre en A4.
 */
public enum FormatoTicket implements Descriptible {

    TERMICO_58(58, "Ticket térmico de 58 mm"),
    TERMICO_80(80, "Ticket térmico de 80 mm"),
    A4(null, "Hoja A4");

    /** Ancho del rollo; null para A4. */
    private final Integer milimetros;
    private final String descripcion;

    FormatoTicket(Integer milimetros, String descripcion) {
        this.milimetros = milimetros;
        this.descripcion = descripcion;
    }

    /** @return el ancho del rollo en milímetros, o null si no es un ticket térmico */
    public Integer getMilimetros() {
        return milimetros;
    }

    /** @return true si se imprime en el rollo de una impresora térmica */
    public boolean esTermico() {
        return milimetros != null;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    /** El formato por defecto (el rollo más común). */
    public static FormatoTicket porDefecto() {
        return TERMICO_80;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
