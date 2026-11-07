package org.gestionComercio.util.dialog;

import org.gestionComercio.validation.ValidationError;
import org.gestionComercio.validation.ValidationResult;

public final class ValidationDialog {

    private ValidationDialog() {
    }

    public static void show(ValidationResult result) {

        Dialogs.lista(
                Dialogs.Tipo.ADVERTENCIA,
                "Revisá los datos ingresados",
                result.getErrors().size() == 1
                        ? "Hay un dato para corregir:"
                        : "Hay " + result.getErrors().size() + " datos para corregir:",
                result.getErrors().stream()
                        .map(ValidationError::getMessage)
                        .toList());
    }
}
