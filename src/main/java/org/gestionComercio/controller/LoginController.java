package org.gestionComercio.controller;

import lombok.Getter;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.UsuarioService;
import org.springframework.stereotype.Component;

@Component
public class LoginController extends AbstractController {

    private final UsuarioService usuarioService;
    private final SesionUsuario sesionUsuario;
    private final Navigator navigator;

    public LoginController(
            UsuarioService usuarioService,
            SesionUsuario sesionUsuario,
            Navigator navigator) {

        this.usuarioService = usuarioService;
        this.sesionUsuario = sesionUsuario;
        this.navigator = navigator;
    }

}