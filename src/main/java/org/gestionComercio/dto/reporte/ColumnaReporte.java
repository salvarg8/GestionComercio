package org.gestionComercio.dto.reporte;

/**
 * Columna de un reporte. Su ancho no se fija de antemano: la pantalla, el PDF
 * y el Excel lo calculan según el texto más largo que muestra (ver
 * {@link Reporte#textoMasLargo(int)}).
 *
 * @param titulo  encabezado
 * @param formato tipo de dato de sus celdas
 */
public record ColumnaReporte(String titulo, FormatoDato formato) {

    public static ColumnaReporte texto(String titulo) {
        return new ColumnaReporte(titulo, FormatoDato.TEXTO);
    }

    public static ColumnaReporte de(String titulo, FormatoDato formato) {
        return new ColumnaReporte(titulo, formato);
    }

    /** Las columnas de texto se pueden achicar (cortando el texto) o agrandar para ocupar el ancho disponible. */
    public boolean esAjustable() {
        return formato == FormatoDato.TEXTO;
    }
}
