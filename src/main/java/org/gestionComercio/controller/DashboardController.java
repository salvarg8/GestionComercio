package org.gestionComercio.controller;

import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.springframework.stereotype.Component;

@Component
public class DashboardController extends AbstractController {

    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;

    public DashboardController(
            Navigator navigator,
            SesionUsuario sesionUsuario) {

        this.navigator = navigator;
        this.sesionUsuario = sesionUsuario;
    }

}