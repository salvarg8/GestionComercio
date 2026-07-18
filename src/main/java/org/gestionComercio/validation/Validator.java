package org.gestionComercio.validation;

public final class Validator {

    private Validator() {
    }

    public static void required(
            ValidationResult result,
            String value,
            String field,
            String message) {

        if (value == null || value.isBlank()) {
            result.addError(field, message);
        }

    }

    public static void positive(
            ValidationResult result,
            Number value,
            String field,
            String message) {

        if (value == null || value.doubleValue() <= 0) {
            result.addError(field, message);
        }

    }

    public static void notNull(
            ValidationResult result,
            Object value,
            String field,
            String message) {

        if (value == null) {
            result.addError(field, message);
        }

    }

}