package org.gestionComercio.service;

import org.gestionComercio.entity.Usuario;

public interface AuthenticationService {

    Usuario authenticate(String username, String password);

}
