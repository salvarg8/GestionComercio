package org.gestionComercio.service;

import org.gestionComercio.dto.permiso.PermisoDto;

import java.util.List;

public interface PermisoService {

    List<PermisoDto> findAll();

}
