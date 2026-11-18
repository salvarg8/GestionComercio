package org.gestionComercio.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Conversión entre números y el texto que escribe o ve el usuario, con el
 * formato argentino (coma decimal, punto de miles).
 */
public final class NumberUtils {

    private static final Locale LOCALE_AR = new Locale("es", "AR");

    private NumberUtils() {
    }

    /**
     * Interpreta un importe escrito por el usuario. Acepta "1234,5", "1.234,50",
     * "1234.5" y "$ 1.234,50". Si hay punto y coma, el punto es de miles; si hay
     * un solo separador, es el decimal.
     *
     * @return el número, o null si el texto está vacío
     * @throws NumberFormatException si el texto no es un número válido
     */
    public static BigDecimal parseDecimal(String texto) {

        if (texto == null) {
            return null;
        }

        String limpio = texto.replace("$", "").replaceAll("\\s", "");

        if (limpio.isEmpty()) {
            return null;
        }

        if (limpio.contains(",") && limpio.contains(".")) {
            limpio = limpio.replace(".", "").replace(",", ".");
        } else {
            limpio = limpio.replace(",", ".");
        }

        if (!limpio.matches("-?\\d+(\\.\\d+)?|-?\\.\\d+")) {
            throw new NumberFormatException("Número inválido: " + texto);
        }

        return new BigDecimal(limpio);
    }

    /**
     * Interpreta un número entero escrito por el usuario.
     *
     * @return el número, o null si el texto está vacío
     * @throws NumberFormatException si el texto no es un entero válido
     */
    public static Integer parseEntero(String texto) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        return Integer.valueOf(texto.trim());
    }

    /** Importe para editar en un campo de texto: "1234,50" (sin miles ni símbolo). */
    public static String formatDecimalEditable(BigDecimal valor) {

        if (valor == null) {
            return "";
        }

        DecimalFormat formato = new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(LOCALE_AR));

        return formato.format(valor);
    }
}
