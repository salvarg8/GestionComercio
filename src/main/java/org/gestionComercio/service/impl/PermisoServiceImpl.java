package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.permiso.PermisoDto;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.mapper.PermisoMapper;
import org.gestionComercio.repository.PermisoRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.PermisoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermisoServiceImpl implements PermisoService {

    private final PermisoRepository permisoRepository;
    private final PermisoMapper permisoMapper;
    private final SesionUsuario sesionUsuario;

    @Override
    public List<PermisoDto> findAll() {

        requerirLectura();

        return permisoRepository.findAll()
                .stream()
                .map(permisoMapper::toDto)
                .sorted(Comparator.comparing(PermisoDto::getCodigo))
                .toList();
    }

    /**
     * Los permisos se consultan desde el formulario de roles: para asignarlos
     * (ROL_CREAR, ROL_EDITAR) o para ver los de un rol (ROL_VER).
     */
    private void requerirLectura() {
        sesionUsuario.requerirAlgunPermiso(
                PermisoCodigo.ROL_VER,
                PermisoCodigo.ROL_CREAR,
                PermisoCodigo.ROL_EDITAR);
    }
}
