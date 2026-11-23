package org.gestionComercio.enums;

import java.util.List;

/**
 * Rubro del comercio, para cargar datos iniciales al empezar a usar el
 * sistema: las categorías típicas y la unidad de medida que se propone para
 * los productos nuevos.
 */
public enum Rubro implements Descriptible {

    ALMACEN("Almacén / autoservicio", UnidadMedida.UNIDAD, List.of(
            "Almacén", "Bebidas", "Lácteos", "Fiambrería", "Panificados", "Golosinas",
            "Limpieza", "Perfumería", "Congelados")),

    KIOSCO("Kiosco", UnidadMedida.UNIDAD, List.of(
            "Golosinas", "Bebidas", "Cigarrillos", "Galletitas", "Snacks", "Helados", "Varios")),

    VERDULERIA("Verdulería / frutería", UnidadMedida.KILOGRAMO, List.of(
            "Frutas", "Verduras", "Hortalizas de hoja", "Huevos", "Frutos secos")),

    CARNICERIA("Carnicería / granja", UnidadMedida.KILOGRAMO, List.of(
            "Vacuno", "Cerdo", "Pollo", "Embutidos", "Achuras", "Congelados")),

    FERRETERIA("Ferretería", UnidadMedida.UNIDAD, List.of(
            "Herramientas manuales", "Herramientas eléctricas", "Tornillería", "Electricidad",
            "Plomería", "Pinturería", "Jardín", "Cerrajería")),

    LIBRERIA("Librería", UnidadMedida.UNIDAD, List.of(
            "Escolar", "Oficina", "Papelería", "Arte y dibujo", "Fotocopias e impresiones", "Regalería")),

    INDUMENTARIA("Indumentaria / calzado", UnidadMedida.UNIDAD, List.of(
            "Remeras", "Pantalones", "Abrigos", "Ropa interior", "Calzado", "Accesorios")),

    FARMACIA("Perfumería / farmacia", UnidadMedida.UNIDAD, List.of(
            "Higiene personal", "Cuidado capilar", "Cosmética", "Bebés", "Venta libre", "Accesorios"));

    private final String descripcion;
    private final UnidadMedida unidadPorDefecto;
    private final List<String> categorias;

    Rubro(String descripcion, UnidadMedida unidadPorDefecto, List<String> categorias) {
        this.descripcion = descripcion;
        this.unidadPorDefecto = unidadPorDefecto;
        this.categorias = categorias;
    }

    /** Unidad de medida que se propone al crear un producto. */
    public UnidadMedida getUnidadPorDefecto() {
        return unidadPorDefecto;
    }

    /** Categorías típicas del rubro. */
    public List<String> getCategorias() {
        return categorias;
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
