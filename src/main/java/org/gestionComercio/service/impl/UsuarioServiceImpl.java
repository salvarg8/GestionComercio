package org.gestionComercio.service.impl;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.repository.UsuarioRepository;
import org.gestionComercio.service.UsuarioService;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository repository;

    @Override
    public Usuario login(String usuario, String password) {

        // implementar

        return null;
    }
}