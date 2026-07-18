package org.gestionComercio.service;

import org.gestionComercio.entity.Usuario;

public interface UsuarioService {

    Usuario login(String usuario, String password);

}