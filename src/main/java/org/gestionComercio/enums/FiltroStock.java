package org.gestionComercio.enums;

/**
 * Qué productos mostrar en la consulta de stock según su situación
 * (ver {@link SituacionStock}).
 */
public enum FiltroStock implements Descriptible {

    TODOS("Todos"),

    /** En el mínimo o por debajo, incluidos los que no tienen stock. */
    STOCK_BAJO("Stock bajo y sin stock"),

    /** Stock en cero o negativo. */
    SIN_STOCK("Sin stock");

    private final String descripcion;

    FiltroStock(String descripcion) {
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
