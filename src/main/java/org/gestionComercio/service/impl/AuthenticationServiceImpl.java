package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoUsuario;
import org.gestionComercio.exception.AuthenticationException;
import org.gestionComercio.repository.UsuarioRepository;
import org.gestionComercio.service.AuthenticationService;
import org.gestionComercio.service.PasswordService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordService passwordService;

    @Override
    public Usuario authenticate(String username, String password) {
        Usuario usuario = usuarioRepository.findByUsuario(username)
                .orElseThrow(() ->
                        new AuthenticationException("Usuario o contraseña incorrectos."));

        if (usuario.getEstadoUsuario() != EstadoUsuario.ACTIVO) {
            throw new AuthenticationException("El usuario no está activo.");
        }

        if (!passwordService.matches(password, usuario.getPassword())) {
            throw new AuthenticationException("Usuario o contraseña incorrectos.");
        }

        return usuario;
    }
}