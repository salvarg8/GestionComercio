package org.gestionComercio.enums;

public enum CondicionIVA implements Descriptible {

    RESPONSABLE_INSCRIPTO("Responsable inscripto"),
    MONOTRIBUTISTA("Monotributista"),
    EXENTO("Exento"),
    CONSUMIDOR_FINAL("Consumidor final");

    private final String descripcion;

    CondicionIVA(String descripcion) {
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
