package org.gestionComercio.util;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static boolean isNull(Object value) {
        return value == null;
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    public static boolean isPositive(Number value) {

        return value != null &&
                value.doubleValue() > 0;

    }

    public static boolean isEmail(String email) {

        if (isBlank(email)) {
            return false;
        }

        return email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }

}