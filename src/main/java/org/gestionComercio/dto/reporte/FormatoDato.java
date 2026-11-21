package org.gestionComercio.dto.reporte;

import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.DateUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Tipo de dato de una columna de reporte: define cómo se muestra en pantalla
 * y en PDF, y qué formato numérico lleva en Excel.
 */
public enum FormatoDato {

    TEXTO(false),

    /** Long: cantidades de ventas, compras, productos. */
    ENTERO(true),

    /** BigDecimal: cantidades de stock (hasta tres decimales). */
    CANTIDAD(true),

    /** BigDecimal: importes en pesos. */
    IMPORTE(true),

    /** BigDecimal como fracción: 0.25 se muestra "25,00 %". */
    PORCENTAJE(true),

    /** LocalDate. */
    FECHA(false),

    /** LocalDateTime. */
    FECHA_HORA(false);

    private static final Locale LOCALE_AR = Locale.of("es", "AR");

    private final boolean numerico;

    FormatoDato(boolean numerico) {
        this.numerico = numerico;
    }

    /** @return true si se alinea a la derecha y en Excel va como número. */
    public boolean esNumerico() {
        return numerico;
    }

    /**
     * Texto para mostrar el valor. Null se muestra vacío; un texto en una
     * columna numérica (por ejemplo, una leyenda en la fila de totales) se
     * muestra tal cual.
     */
    public String formatear(Object valor) {

        if (valor == null) {
            return "";
        }

        if (valor instanceof String texto) {
            return texto;
        }

        return switch (this) {
            case TEXTO, ENTERO -> valor.toString();
            case CANTIDAD -> CantidadUtils.formatear((BigDecimal) valor);
            case IMPORTE -> CurrencyUtils.format((BigDecimal) valor);
            case PORCENTAJE -> porcentaje((BigDecimal) valor);
            case FECHA -> DateUtils.format((LocalDate) valor);
            case FECHA_HORA -> DateUtils.format((LocalDateTime) valor);
        };
    }

    private static String porcentaje(BigDecimal fraccion) {

        NumberFormat formato = NumberFormat.getPercentInstance(LOCALE_AR);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);

        return formato.format(fraccion);
    }
}
