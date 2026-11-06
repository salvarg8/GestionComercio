package org.gestionComercio.security;

import lombok.Getter;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthorizationException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Component
public class SesionUsuario {

    @Getter
    private Usuario usuario;

    private Set<PermisoCodigo> permisos =
            EnumSet.noneOf(PermisoCodigo.class);

    public void iniciarSesion(
            Usuario usuario,
            Set<PermisoCodigo> permisos
    ) {
        this.usuario = usuario;

        this.permisos = permisos == null || permisos.isEmpty()
                ? EnumSet.noneOf(PermisoCodigo.class)
                : EnumSet.copyOf(permisos);
    }

    public void cerrarSesion() {
        usuario = null;
        permisos.clear();
    }

    public boolean estaAutenticado() {
        return usuario != null;
    }

    public boolean tienePermiso(PermisoCodigo codigo) {
        return codigo == null || permisos.contains(codigo);
    }

    /**
     * Devuelve true si el usuario tiene al menos uno de los permisos indicados,
     * o si no se exige ninguno.
     */
    public boolean tieneAlgunPermiso(Collection<PermisoCodigo> codigos) {
        return codigos == null
                || codigos.isEmpty()
                || codigos.stream().anyMatch(permisos::contains);
    }

    /**
     * Lanza AuthorizationException si el usuario no tiene el permiso indicado.
     */
    public void requerirPermiso(PermisoCodigo codigo) {
        if (!estaAutenticado() || !tienePermiso(codigo)) {
            throw new AuthorizationException(
                    "No tiene permiso para realizar esta acción.");
        }
    }

    /**
     * Lanza AuthorizationException si el usuario no tiene ninguno de los permisos indicados.
     */
    public void requerirAlgunPermiso(PermisoCodigo... codigos) {
        if (!estaAutenticado() || !tieneAlgunPermiso(Set.of(codigos))) {
            throw new AuthorizationException(
                    "No tiene permiso para realizar esta acción.");
        }
    }

    public Set<PermisoCodigo> getPermisos() {
        return Collections.unmodifiableSet(permisos);
    }
}