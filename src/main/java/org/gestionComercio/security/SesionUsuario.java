package org.gestionComercio.security;

import lombok.Getter;
import lombok.Setter;
import org.gestionComercio.entity.Usuario;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class SesionUsuario {

    private Usuario usuario;

}