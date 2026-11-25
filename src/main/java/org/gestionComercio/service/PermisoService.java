package org.gestionComercio.service;

import org.gestionComercio.dto.permiso.PermisoDto;

import java.util.List;

public interface PermisoService {

    /** Todos los permisos, en el orden en que se declaran en PermisoCodigo (agrupados por módulo). */
    List<PermisoDto> findAll();

}
