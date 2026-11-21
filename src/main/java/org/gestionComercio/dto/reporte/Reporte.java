package org.gestionComercio.dto.reporte;

import org.gestionComercio.enums.TipoReporte;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Resultado de un reporte en forma de tabla. La pantalla, el PDF y el Excel
 * se arman a partir de este mismo objeto, así que muestran los mismos datos
 * y los mismos totales.
 *
 * <p>Cada celda es del tipo que indica el formato de su columna (Long,
 * BigDecimal, LocalDate, LocalDateTime o String), o null si está vacía; una
 * celda String en una columna numérica es una leyenda.
 *
 * @param tipo     reporte generado
 * @param empresa  razón social de la empresa, para el encabezado
 * @param filtros  período o momento al que corresponden los datos ("Del 01/11/2026 al 30/11/2026")
 * @param generado cuándo se generó
 * @param columnas columnas, en orden
 * @param filas    filas de datos, cada una con una celda por columna
 * @param totales  fila de totales, o null si el reporte no tiene
 */
public record Reporte(
        TipoReporte tipo,
        String empresa,
        String filtros,
        LocalDateTime generado,
        List<ColumnaReporte> columnas,
        List<List<Object>> filas,
        List<Object> totales) {

    public Reporte {

        Objects.requireNonNull(tipo, "tipo");
        columnas = List.copyOf(columnas);
        filas = List.copyOf(filas);

        for (List<Object> fila : filas) {
            validarAncho(fila, columnas.size());
        }

        if (totales != null) {
            validarAncho(totales, columnas.size());
        }
    }

    public String titulo() {
        return tipo.getDescripcion();
    }

    public boolean tieneTotales() {
        return totales != null;
    }

    /**
     * Texto más largo de la columna tal como se muestra (filas y totales, sin
     * el título), para darle a la columna el ancho que necesita su contenido.
     *
     * @return "" si la columna no tiene datos
     */
    public String textoMasLargo(int columna) {

        FormatoDato formato = columnas.get(columna).formato();
        String masLargo = "";

        for (List<Object> fila : filas) {
            masLargo = elMasLargo(masLargo, formato.formatear(fila.get(columna)));
        }

        if (totales != null) {
            masLargo = elMasLargo(masLargo, formato.formatear(totales.get(columna)));
        }

        return masLargo;
    }

    private static String elMasLargo(String a, String b) {
        return b != null && b.length() > a.length() ? b : a;
    }

    /** Fila inmodificable que admite celdas vacías (null). */
    public static List<Object> fila(Object... celdas) {
        return Collections.unmodifiableList(Arrays.asList(celdas));
    }

    private static void validarAncho(List<Object> fila, int columnas) {

        if (fila.size() != columnas) {
            throw new IllegalArgumentException(
                    "La fila tiene " + fila.size() + " celdas y el reporte " + columnas + " columnas.");
        }
    }
}
