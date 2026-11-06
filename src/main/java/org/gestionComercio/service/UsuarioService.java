package org.gestionComercio.service;

import org.gestionComercio.dto.rol.RolDto;
import org.gestionComercio.dto.usuario.UsuarioDto;

import java.util.List;

public interface UsuarioService extends CrudService<UsuarioDto, Long> {

    /**
     * Roles activos que se pueden asignar desde el formulario de usuarios.
     * Requiere USUARIO_CREAR o USUARIO_EDITAR (no ROL_VER).
     */
    List<RolDto> findRolesDisponibles();

    /**
     * Cambia la contraseña del usuario que tiene la sesión iniciada.
     * No requiere permisos especiales, solo estar autenticado y conocer la contraseña actual.
     */
    void cambiarPassword(String passwordActual, String passwordNueva);

}
