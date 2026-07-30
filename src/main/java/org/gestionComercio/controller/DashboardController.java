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
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
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
    private Button btnCerrarSesion;

    @Override
    protected void initializeComponents() {

        navigator.setContentPane(contentPane);

        lblUsuario.setText(
                sesionUsuario.getUsuario().getNombre()
        );

        // Opcional: vista inicial
        // navigator.navigate(AppView.INICIO);
    }

    @Override
    protected void initializeEvents() {

        btnRoles.setOnAction(e ->
                navigator.navigate(AppView.ROL_LIST));

        btnCerrarSesion.setOnAction(e -> cerrarSesion());
    }

    private void cerrarSesion() {

        logoutService.logout();

        navigator.navigate(AppView.LOGIN);
    }
}