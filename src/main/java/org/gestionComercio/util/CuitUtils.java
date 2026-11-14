package org.gestionComercio.util;

/**
 * CUIT/CUIL argentino: 11 dígitos, el último es verificador (módulo 11).
 * Internamente se guarda sin guiones; para mostrar se usa XX-XXXXXXXX-X.
 */
public final class CuitUtils {

    public static final int LARGO = 11;

    private static final int[] PESOS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private CuitUtils() {
    }

    /**
     * Quita guiones, puntos y espacios. Devuelve null si no queda nada.
     * No quita letras u otros caracteres: así un CUIT mal escrito no pasa la validación.
     */
    public static String normalizar(String cuit) {

        if (cuit == null) {
            return null;
        }

        String limpio = cuit.replaceAll("[\\s.\\-]", "");

        return limpio.isEmpty() ? null : limpio;
    }

    /** true si tiene 11 dígitos y el dígito verificador es correcto. */
    public static boolean esValido(String cuit) {

        String normalizado = normalizar(cuit);

        if (normalizado == null || !normalizado.matches("\\d{" + LARGO + "}")) {
            return false;
        }

        int suma = 0;

        for (int i = 0; i < PESOS.length; i++) {
            suma += Character.getNumericValue(normalizado.charAt(i)) * PESOS[i];
        }

        int resto = 11 - (suma % 11);
        int verificador = switch (resto) {
            case 11 -> 0;
            case 10 -> 9;
            default -> resto;
        };

        return verificador == Character.getNumericValue(normalizado.charAt(LARGO - 1));
    }

    /** XX-XXXXXXXX-X si son 11 dígitos; si no, el texto tal cual (o vacío si es null). */
    public static String formatear(String cuit) {

        String normalizado = normalizar(cuit);

        if (normalizado == null) {
            return "";
        }

        if (!normalizado.matches("\\d{" + LARGO + "}")) {
            return cuit;
        }

        return normalizado.substring(0, 2) + "-"
                + normalizado.substring(2, 10) + "-"
                + normalizado.substring(10);
    }
}
