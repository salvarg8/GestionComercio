package org.gestionComercio.validation;

public final class ValidationUtils {

    private ValidationUtils() {
    }

    public static void required(
            String value,
            String field,
            String message,
            ValidationResult result) {

        if (value == null || value.isBlank()) {
            result.addError(field, message);
        }
    }

    public static void notNull(
            Object value,
            String field,
            String message,
            ValidationResult result) {

        if (value == null) {
            result.addError(field, message);
        }
    }

    public static void nonNegative(
            Number value,
            String field,
            String message,
            ValidationResult result) {

        if (value == null || value.doubleValue() < 0) {
            result.addError(field, message);
        }
    }

}