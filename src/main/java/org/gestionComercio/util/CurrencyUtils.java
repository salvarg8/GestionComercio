package org.gestionComercio.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

public final class CurrencyUtils {

    private static final Locale LOCALE_AR = new Locale("es", "AR");

    private CurrencyUtils() {
    }

    public static String format(BigDecimal value) {

        if (value == null) {
            return "$ 0,00";
        }

        return NumberFormat
                .getCurrencyInstance(LOCALE_AR)
                .format(value);

    }

}