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
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermisoDto> search(String texto) {

        if (texto == null || texto.isBlank()) {
            return findAll();
        }

        String filtro = texto.trim().toLowerCase();

        return findAll().stream()
                .filter(p ->
                        p.getCodigo().name().toLowerCase().contains(filtro)
                                || p.getDescripcion().toLowerCase().contains(filtro))
                .toList();
    }

    /**
     * El formulario de roles también necesita listar los permisos para
     * asignarlos, así que se acepta PERMISO_VER, ROL_CREAR o ROL_EDITAR.
     */
    private void requerirLectura() {
        sesionUsuario.requerirAlgunPermiso(
                PermisoCodigo.PERMISO_VER,
                PermisoCodigo.ROL_CREAR,
                PermisoCodigo.ROL_EDITAR);
    }
}
