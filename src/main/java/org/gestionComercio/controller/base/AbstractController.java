package org.gestionComercio.controller.base;

import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.util.AlertUtils;
import org.gestionComercio.util.dialog.ValidationDialog;

import java.net.URL;
import java.util.ResourceBundle;

public abstract class AbstractController
        implements Initializable, ViewController {

    @Override
    public final void initialize(URL location,
                                 ResourceBundle resources) {

        initializeComponents();
        initializeBindings();
        initializeEvents();
    }

    protected void initializeComponents() { }

    protected void initializeBindings() { }

    protected void initializeEvents() { }

    protected void info(String mensaje) {
        AlertUtils.information(mensaje);
    }

    protected void warning(String mensaje) {
        AlertUtils.warning("Atención", mensaje);
    }

    protected void error(String mensaje) {
        AlertUtils.error(mensaje);
    }

    @FunctionalInterface
    protected interface ControllerAction {
        void execute();
    }

    protected void execute(ControllerAction action) {

        try {

            action.execute();

        } catch (ValidationException ex) {

            ValidationDialog.show(ex.getValidationResult());

        } catch (BusinessException ex) {

            AlertUtils.error(ex.getMessage());

        } catch (Exception ex) {

            AlertUtils.error("Ocurrió un error inesperado.");
        }
    }

    protected boolean confirm(String titulo,
                              String mensaje) {

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);

        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);

        return alert.showAndWait()
                .filter(ButtonType.OK::equals)
                .isPresent();
    }

}