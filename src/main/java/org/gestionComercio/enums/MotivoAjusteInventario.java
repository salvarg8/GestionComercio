package org.gestionComercio.enums;

/**
 * Causa de un ajuste de stock. Es una lista cerrada para poder agrupar los
 * ajustes en reportes (cuánto se pierde por vencimiento, por roturas, etc.);
 * el detalle libre va en el texto del motivo.
 */
public enum MotivoAjusteInventario implements Descriptible {

    RECUENTO("Recuento físico"),
    ROTURA("Rotura"),
    VENCIMIENTO("Vencimiento"),
    ROBO_PERDIDA("Robo o pérdida"),
    CONSUMO_INTERNO("Consumo interno"),
    OTRO("Otro");

    private final String descripcion;

    MotivoAjusteInventario(String descripcion) {
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
