package org.gestionComercio.controller.base;

import javafx.fxml.Initializable;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.util.dialog.Dialogs;
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
        Dialogs.informacion("Información", mensaje);
    }

    /** Confirmación de una operación que salió bien (guardar, eliminar, etc.). */
    protected void success(String mensaje) {
        Dialogs.exito("Operación realizada", mensaje);
    }

    protected void warning(String mensaje) {
        Dialogs.advertencia("Atención", mensaje);
    }

    protected void error(String mensaje) {
        Dialogs.error("Error", mensaje);
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

            // Reglas de negocio y permisos: el usuario puede resolverlo, no es una falla.
            Dialogs.advertencia("No se pudo completar la operación", ex.getMessage());

        } catch (Exception ex) {

            Dialogs.error("Ocurrió un error inesperado",
                    "Si el problema persiste, comuníquese con soporte.");
        }
    }

    protected boolean confirm(String titulo,
                              String mensaje) {

        return Dialogs.confirmar(titulo, mensaje);
    }

    /** Confirmación para acciones destructivas: botón rojo y "Cancelar" por defecto. */
    protected boolean confirmDelete(String titulo,
                                    String mensaje) {

        return Dialogs.confirmar(titulo, mensaje, "Eliminar", true);
    }

}
