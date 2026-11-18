package org.gestionComercio.controller.base;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import org.gestionComercio.util.dialog.ValidationDialog;
import org.gestionComercio.validation.ValidationError;
import org.gestionComercio.validation.ValidationResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Errores de validación junto a cada campo de un formulario: el mensaje va en
 * una etiqueta debajo del campo y el control se marca con borde rojo.
 * <p>
 * Uso: cada controlador crea una instancia, registra sus campos con el nombre
 * que usa el validador (el campo del DTO) y llama a {@link #mostrar(ValidationResult)}
 * cuando el servicio rechaza los datos.
 */
public class ErroresPorCampo {

    /** Clase CSS del control con error (ver forms.css). */
    public static final String CLASE_INVALIDO = "field-invalid";

    private record Campo(Control control, Label lblError) {
    }

    /** Campo del DTO → control y etiqueta donde se muestra su error. */
    private final Map<String, Campo> campos = new LinkedHashMap<>();

    /**
     * Registra un campo. Su error se borra en cuanto cambia {@code valor}
     * (el texto o el valor elegido), es decir, cuando el usuario lo corrige.
     */
    public ErroresPorCampo registrar(String campo, Control control, Label lblError, ObservableValue<?> valor) {

        registrar(campo, control, lblError);

        valor.addListener((obs, o, n) -> limpiar(campo));

        return this;
    }

    /** Registra un campo cuyo error sólo se borra con {@link #limpiar}. */
    public ErroresPorCampo registrar(String campo, Control control, Label lblError) {

        campos.put(campo, new Campo(control, lblError));

        return this;
    }

    /**
     * Muestra cada error debajo de su campo y pone el foco en el primero. Si
     * alguno no corresponde a un campo del formulario, se muestran todos en la
     * ventana de validación habitual.
     */
    public void mostrar(ValidationResult result) {

        if (!marcar(result)) {
            ValidationDialog.show(result);
        }

        result.getErrors().stream()
                .map(error -> campos.get(error.getField()))
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(campo -> campo.control().requestFocus());
    }

    /**
     * Muestra los errores debajo de sus campos, sin ventana ni cambio de foco.
     *
     * @return true si todos los errores corresponden a un campo registrado.
     */
    public boolean marcar(ValidationResult result) {

        boolean todosUbicados = true;

        for (ValidationError error : result.getErrors()) {

            if (campos.containsKey(error.getField())) {
                mostrar(error.getField(), error.getMessage());
            } else {
                todosUbicados = false;
            }
        }

        return todosUbicados;
    }

    /**
     * Muestra un error en un campo registrado. Si el campo ya tenía otro error,
     * se muestran los dos.
     */
    public void mostrar(String campo, String mensaje) {

        Campo registrado = campos.get(campo);

        if (registrado == null) {
            throw new IllegalArgumentException("Campo no registrado en el formulario: " + campo);
        }

        Label lblError = registrado.lblError();
        String anterior = lblError.getText();

        lblError.setText(anterior == null || anterior.isEmpty() ? mensaje : anterior + "\n" + mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);

        Control control = registrado.control();
        if (!control.getStyleClass().contains(CLASE_INVALIDO)) {
            control.getStyleClass().add(CLASE_INVALIDO);
        }
    }

    public boolean tieneError(String campo) {

        Campo registrado = campos.get(campo);

        return registrado != null && registrado.lblError().isVisible();
    }

    /** Borra los errores de todos los campos. */
    public void limpiar() {
        campos.keySet().forEach(this::limpiar);
    }

    /** Borra el error de un campo (si no está registrado, no hace nada). */
    public void limpiar(String campo) {

        Campo registrado = campos.get(campo);

        if (registrado == null) {
            return;
        }

        registrado.lblError().setText("");
        registrado.lblError().setVisible(false);
        registrado.lblError().setManaged(false);
        registrado.control().getStyleClass().remove(CLASE_INVALIDO);
    }
}
