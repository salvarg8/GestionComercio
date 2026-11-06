package org.gestionComercio.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.LogoutService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Scope("prototype")
public class DashboardController extends AbstractController {

    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;
    private final LogoutService logoutService;

    @FXML
    private StackPane contentPane;

    @FXML
    private Label lblUsuario;

    @FXML
    private Button btnRoles;

    @FXML
    private Button btnPermisos;

    @FXML
    private Button btnUsuarios;

    @FXML
    private Button btnCambiarPassword;

    @FXML
    private Button btnCerrarSesion;

    @Override
    protected void initializeComponents() {

        navigator.setContentPane(contentPane);

        lblUsuario.setText(
                sesionUsuario.getUsuario().getNombre()
        );

        mostrarSiTieneAcceso(btnRoles, AppView.ROL_LIST);
        mostrarSiTieneAcceso(btnPermisos, AppView.PERMISO_LIST);
        mostrarSiTieneAcceso(btnUsuarios, AppView.USUARIO_LIST);

        // Opcional: vista inicial
        // navigator.navigate(AppView.INICIO);
    }

    @Override
    protected void initializeEvents() {

        btnRoles.setOnAction(e ->
                navigator.navigate(AppView.ROL_LIST));

        btnPermisos.setOnAction(e -> navigator.navigate(AppView.PERMISO_LIST));

        btnUsuarios.setOnAction(e -> navigator.navigate(AppView.USUARIO_LIST));

        btnCambiarPassword.setOnAction(e -> navigator.navigate(AppView.CAMBIAR_PASSWORD));

        btnCerrarSesion.setOnAction(e -> cerrarSesion());
    }

    private void mostrarSiTieneAcceso(Button boton, AppView view) {
        boolean visible = navigator.puedeAcceder(view);
        boton.setVisible(visible);
        boton.setManaged(visible);
    }

    private void cerrarSesion() {

        logoutService.logout();

        // El contenedor pertenece a este Dashboard; el próximo login crea uno nuevo.
        navigator.setContentPane(null);

        navigator.navigate(AppView.LOGIN);
    }
}