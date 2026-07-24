package org.gestionComercio.controller;

import javafx.fxml.FXML;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.LogoutService;
import org.springframework.stereotype.Component;

@Component
public class DashboardController extends AbstractController {

    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;
    private final LogoutService logoutService;

    public DashboardController(
            Navigator navigator,
            SesionUsuario sesionUsuario,
            LogoutService logoutService) {

        this.navigator = navigator;
        this.sesionUsuario = sesionUsuario;
        this.logoutService = logoutService;
    }

    @FXML
    private void cerrarSesion() {

        logoutService.logout();

        navigator.navigate(AppView.LOGIN);
    }

}