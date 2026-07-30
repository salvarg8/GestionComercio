package org.gestionComercio.util.dialog;

import javafx.scene.control.Alert;
import org.gestionComercio.validation.ValidationResult;

public final class ValidationDialog {

    private ValidationDialog() {
    }

    public static void show(ValidationResult result) {

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Validación");
        alert.setHeaderText("Se encontraron errores");
        alert.setContentText(result.getMessage());

        alert.showAndWait();
    }
}