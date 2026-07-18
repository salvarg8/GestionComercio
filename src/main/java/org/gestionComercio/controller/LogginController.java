package org.gestionComercio.controller;

import org.gestionComercio.entity.Usuario;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.impl.UsuarioServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LogginController {

    @Autowired
    private SesionUsuario sesionUsuario;

    @Autowired
    private UsuarioServiceImpl usuarioService;


    public void validarUsuario(String usuario, String password) {

        Usuario usr = usuarioService.login(usuario, password);

        sesionUsuario.setUsuario(usr);
    }
}
