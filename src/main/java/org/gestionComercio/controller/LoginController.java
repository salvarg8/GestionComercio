package org.gestionComercio.controller;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.entity.Permiso;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthenticationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.AuthenticationService;
import org.gestionComercio.util.dialog.Dialogs;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Scope("prototype")
public class LoginController extends AbstractController {

    @FXML
    private TextField txtUsuario;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private TextField txtPasswordVisible;

    @FXML
    private CheckBox chkMostrarNoMostrarContraseña;

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
        System.out.println("Constructor -> " + this);

    }

    @FXML
    protected void initializeBindings() {
        System.out.println("initialize");
        System.out.println(txtPassword);
        System.out.println(txtPasswordVisible);

        txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());
    }

    @Override
    public void onShow() {
        txtUsuario.requestFocus();
    }

    /**
     * Método invocado desde el botón Ingresar.
     */
    @FXML
    private void ingresar() {

        try {
            performLogin(txtUsuario.getText(), txtPassword.getText());

        } catch (AuthenticationException e) {

            txtPassword.clear();
            txtPassword.requestFocus();

            Dialogs.error("No se pudo iniciar sesión", e.getMessage());
        }
    }

    /**
     * Lógica de autenticación.
     */
    void performLogin(String username, String password) {

        Usuario usuario = authenticationService.authenticate(username, password);

        sesionUsuario.iniciarSesion(usuario, obtenerPermisos(usuario));

        navigator.navigate(AppView.DASHBOARD);
    }

    private Set<PermisoCodigo> obtenerPermisos(Usuario usuario) {
        if (usuario.getRol() == null || usuario.getRol().getPermisos() == null) {
            return Collections.emptySet();
        }

        return usuario.getRol().getPermisos().stream()
                .map(Permiso::getCodigo)
                .collect(Collectors.toSet());
    }

    /**
     * Muestra u oculta la contraseña.
     */
    @FXML
    private void mostrarNoMostrarContraseña() {

        boolean mostrar = chkMostrarNoMostrarContraseña.isSelected();

        txtPassword.setVisible(!mostrar);
        txtPassword.setManaged(!mostrar);

        txtPasswordVisible.setVisible(mostrar);
        txtPasswordVisible.setManaged(mostrar);

        if (mostrar) {
            txtPasswordVisible.requestFocus();
            txtPasswordVisible.positionCaret(txtPasswordVisible.getText().length());
        } else {
            txtPassword.requestFocus();
            txtPassword.positionCaret(txtPassword.getText().length());
        }
    }
}