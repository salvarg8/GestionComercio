package org.gestionComercio.enums;

public enum UnidadMedida implements Descriptible{

    UNIDAD("Unidad"),
    CAJA("Caja"),
    PAQUETE("Paquete"),
    BOLSA("Bolsa"),
    KILOGRAMO("Kilogramo"),
    GRAMO("Gramo"),
    LITRO("Litro"),
    MILILITRO("Mililitro"),
    METRO("Metro"),
    CENTIMETRO("Centímetro"),
    ROLLO("Rollo");

    private final String descripcion;

    UnidadMedida(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}