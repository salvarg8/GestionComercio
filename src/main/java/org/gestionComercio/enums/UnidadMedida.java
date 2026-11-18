package org.gestionComercio.enums;

/**
 * Unidad en la que se mide el stock y se venden los productos. Las unidades
 * fraccionables (kilo, litro, metro) admiten cantidades con decimales, por
 * ejemplo 0,750 kg; el resto sólo admite cantidades enteras.
 */
public enum UnidadMedida implements Descriptible {

    UNIDAD("Unidad", false),
    CAJA("Caja", false),
    PAQUETE("Paquete", false),
    BOLSA("Bolsa", false),
    KILOGRAMO("Kilogramo", true),
    GRAMO("Gramo", false),
    LITRO("Litro", true),
    MILILITRO("Mililitro", false),
    METRO("Metro", true),
    CENTIMETRO("Centímetro", false),
    ROLLO("Rollo", false);

    private final String descripcion;

    private final boolean fraccionable;

    UnidadMedida(String descripcion, boolean fraccionable) {
        this.descripcion = descripcion;
        this.fraccionable = fraccionable;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    /** @return true si admite cantidades con decimales. */
    public boolean isFraccionable() {
        return fraccionable;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
