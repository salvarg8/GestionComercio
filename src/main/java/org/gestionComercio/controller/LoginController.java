package org.gestionComercio.controller;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.exception.AuthenticationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.AuthenticationService;
import org.springframework.stereotype.Component;

@Component
public class LoginController extends AbstractController {

    @FXML
    private TextField txtUsuario;

    @FXML
    private PasswordField txtPassword;

    private final AuthenticationService authenticationService;
    private final SesionUsuario sesionUsuario;
    private final Navigator navigator;

    public LoginController(
            AuthenticationService authenticationService,
            SesionUsuario sesionUsuario,
            Navigator navigator) {
        this.authenticationService = authenticationService;
        this.sesionUsuario = sesionUsuario;
        this.navigator = navigator;
    }

    /**
     * Método FXML. Lee desde la UI, delega a la lógica de negocio
     * y maneja las excepciones para mostrarlas en la UI.
     */
    @FXML
    private void ingresar() {
        try {
            performLogin(txtUsuario.getText(), txtPassword.getText());
        } catch (AuthenticationException e) {
            // La excepción se captura aquí y se muestra al usuario.
            info("Error de autenticación: " + e.getMessage());
        }
    }

    /**
     * Lógica de autenticación pura, sin dependencias de UI.
     * Lanza una excepción si la autenticación falla.
     */
    void performLogin(String username, String password) throws AuthenticationException {
        Usuario usuario = authenticationService.authenticate(username, password);
        sesionUsuario.setUsuario(usuario);
        navigator.navigate(AppView.DASHBOARD);
    }
}
