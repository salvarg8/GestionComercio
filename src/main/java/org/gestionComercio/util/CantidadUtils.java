package org.gestionComercio.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Reglas y formato de las cantidades de stock (stock de productos, cantidades
 * de movimientos y de renglones de comprobantes).
 *
 * <p>Se guardan con hasta {@value #ESCALA} decimales para admitir unidades
 * fraccionables (0,750 kg, 2,5 m). Las columnas usan
 * {@value #PRECISION} dígitos en total, así que la parte entera admite hasta
 * {@value #ENTEROS_MAX} dígitos.
 */
public final class CantidadUtils {

    public static final int ESCALA = 3;

    public static final int PRECISION = 15;

    public static final int ENTEROS_MAX = PRECISION - ESCALA;

    private static final BigDecimal LIMITE = BigDecimal.TEN.pow(ENTEROS_MAX);

    private static final Locale LOCALE_AR = Locale.of("es", "AR");

    private CantidadUtils() {
    }

    /** @return true si no tiene decimales distintos de cero (5 y 5,000 son enteras). */
    public static boolean esEntera(BigDecimal cantidad) {
        return cantidad.signum() == 0 || cantidad.stripTrailingZeros().scale() <= 0;
    }

    /** @return true si no tiene más de {@value #ESCALA} decimales significativos. */
    public static boolean escalaValida(BigDecimal cantidad) {
        return cantidad.signum() == 0 || cantidad.stripTrailingZeros().scale() <= ESCALA;
    }

    /** @return true si su valor absoluto entra en las columnas de cantidad. */
    public static boolean dentroDelMaximo(BigDecimal cantidad) {
        return cantidad.abs().compareTo(LIMITE) < 0;
    }

    /**
     * Lleva la cantidad a la escala de las columnas. Null se toma como cero.
     *
     * @throws ArithmeticException si tiene más decimales que {@value #ESCALA}
     *                             (validar antes con {@link #escalaValida})
     */
    public static BigDecimal normalizar(BigDecimal cantidad) {
        return cantidad == null
                ? BigDecimal.ZERO.setScale(ESCALA)
                : cantidad.setScale(ESCALA, RoundingMode.UNNECESSARY);
    }

    /** Cantidad para mostrar: "1.234,5" (sin ceros decimales de más). Null se muestra como "0". */
    public static String formatear(BigDecimal cantidad) {
        return formato("#,##0.###").format(cantidad == null ? BigDecimal.ZERO : cantidad);
    }

    /** Cantidad para editar en un campo de texto: "1234,5" (sin separador de miles). */
    public static String formatearEditable(BigDecimal cantidad) {
        return cantidad == null ? "" : formato("0.###").format(cantidad);
    }

    private static DecimalFormat formato(String patron) {
        DecimalFormat formato = new DecimalFormat(patron, DecimalFormatSymbols.getInstance(LOCALE_AR));
        formato.setParseBigDecimal(true);
        return formato;
    }

}
