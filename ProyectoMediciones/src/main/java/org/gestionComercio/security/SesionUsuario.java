package org.gestionComercio.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import lombok.Getter;
import org.gestionComercio.entity.Usuario;
import org.springframework.stereotype.Component;

@Getter
@Component
public class SesionUsuario {
    private Usuario usuario;
    private Set<PermisoCodigo> permisos = Collections.emptySet();

    public void iniciarSesion(Usuario usuario, Set<PermisoCodigo> permisos) {
        this.usuario = Objects.requireNonNull(usuario, "El usuario es obligatorio");

        EnumSet<PermisoCodigo> copiaPermisos = EnumSet.noneOf(PermisoCodigo.class);
        if (permisos != null) {
            copiaPermisos.addAll(permisos);
        }
        this.permisos = Collections.unmodifiableSet(copiaPermisos);
    }

    public boolean estaAutenticado() {
        return usuario != null;
    }

    public boolean tienePermiso(PermisoCodigo permiso) {
        return permiso != null && permisos.contains(permiso);
    }

    public void cerrarSesion() {
        usuario = null;
        permisos = Collections.emptySet();
    }
}
