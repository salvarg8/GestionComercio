package org.gestionComercio.util;

import java.text.Normalizer;

public final class StringUtils {

    private StringUtils() {
    }

    public static String capitalize(String text) {

        if (ValidationUtils.isBlank(text)) {
            return "";
        }

        return text.substring(0 , 1).toUpperCase()
                + text.substring(1).toLowerCase();

    }

    public static String onlyNumbers(String text) {

        if (text == null) {
            return "";
        }

        return text.replaceAll("\\D", "");

    }

    public static String removeAccents(String text) {

        if (text == null) {
            return "";
        }

        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}",  "");

    }

}