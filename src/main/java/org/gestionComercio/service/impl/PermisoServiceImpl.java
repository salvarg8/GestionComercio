package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.permiso.PermisoDto;
import org.gestionComercio.mapper.PermisoMapper;
import org.gestionComercio.repository.PermisoRepository;
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

    @Override
    public List<PermisoDto> findAll() {

        return permisoRepository.findAll()
                .stream()
                .map(permisoMapper::toDto)
                .toList();
    }
}