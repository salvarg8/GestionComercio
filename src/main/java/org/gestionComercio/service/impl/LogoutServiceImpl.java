package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.LogoutService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogoutServiceImpl implements LogoutService {

    private final SesionUsuario sesionUsuario;

    @Override
    public void logout() {
        sesionUsuario.cerrarSesion();
    }
}