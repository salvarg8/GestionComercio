package org.gestionComercio.config;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.security.SesionUsuario;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("auditorProvider")
@RequiredArgsConstructor
public class AuditorProvider implements AuditorAware<Usuario> {

    private final SesionUsuario sesionUsuario;

    @Override
    public Optional<Usuario> getCurrentAuditor() {

        return Optional.ofNullable(sesionUsuario.getUsuario());

    }
}