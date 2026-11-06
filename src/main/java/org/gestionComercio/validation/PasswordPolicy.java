package org.gestionComercio.validation;

/**
 * Reglas de contraseña compartidas por el ABM de usuarios y el cambio de contraseña.
 */
public final class PasswordPolicy {

    public static final int LONGITUD_MINIMA = 6;
    public static final int LONGITUD_MAXIMA = 72; // límite de BCrypt

    private PasswordPolicy() {
    }

    public static void validar(String password, String field, ValidationResult result) {

        if (password == null || password.isBlank()) {
            result.addError(field, "La contraseña es obligatoria.");
            return;
        }

        if (password.length() < LONGITUD_MINIMA) {
            result.addError(field,
                    "La contraseña debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }

        if (password.length() > LONGITUD_MAXIMA) {
            result.addError(field,
                    "La contraseña no puede superar los " + LONGITUD_MAXIMA + " caracteres.");
        }
    }
}
