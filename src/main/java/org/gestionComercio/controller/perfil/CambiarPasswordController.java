package org.gestionComercio.controller.perfil;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.service.UsuarioService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.Objects;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class CambiarPasswordController extends AbstractController {

    private final UsuarioService usuarioService;

    @FXML
    private PasswordField txtPasswordActual;

    @FXML
    private PasswordField txtPasswordNueva;

    @FXML
    private PasswordField txtPasswordConfirmacion;

    @FXML
    private Button btnGuardar;

    @FXML
    private Button btnLimpiar;

    @Override
    protected void initializeComponents() {
        txtPasswordActual.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnLimpiar.setOnAction(e -> limpiar());
    }

    private void guardar() {

        if (!Objects.equals(txtPasswordNueva.getText(), txtPasswordConfirmacion.getText())) {
            warning("La nueva contraseña y su confirmación no coinciden.");
            return;
        }

        execute(() -> {

            usuarioService.cambiarPassword(
                    txtPasswordActual.getText(),
                    txtPasswordNueva.getText());

            limpiar();

            success("La contraseña se cambió correctamente.");
        });
    }

    private void limpiar() {
        txtPasswordActual.clear();
        txtPasswordNueva.clear();
        txtPasswordConfirmacion.clear();
        txtPasswordActual.requestFocus();
    }
}
