package org.gestionComercio.enums;

/**
 * Qué cajas mostrar en el historial de cajas según su estado.
 */
public enum FiltroCaja implements Descriptible {

    TODAS("Todas"),
    ABIERTAS("Abiertas"),
    CERRADAS("Cerradas");

    private final String descripcion;

    FiltroCaja(String descripcion) {
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
