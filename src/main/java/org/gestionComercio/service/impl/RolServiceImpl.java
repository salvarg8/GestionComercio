package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.rol.RolDto;
import org.gestionComercio.entity.Permiso;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.RolMapper;
import org.gestionComercio.repository.PermisoRepository;
import org.gestionComercio.repository.RolRepository;
import org.gestionComercio.service.RolService;
import org.gestionComercio.validation.RolValidator;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class RolServiceImpl implements RolService {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;

    private final RolMapper rolMapper;
    private final RolValidator rolValidator;

    @Override
    public RolDto save(RolDto dto) {

        normalizar(dto);

        validar(dto);

        Rol rol = rolMapper.toEntity(dto);

        cargarPermisos(rol, dto);

        rol = rolRepository.save(rol);

        return rolMapper.toDto(rol);
    }

    @Override
    public RolDto update(RolDto dto) {

        normalizar(dto);

        validar(dto);

        Rol rol = buscarRol(dto.getId());

        rolMapper.updateEntity(dto, rol);

        cargarPermisos(rol, dto);

        rol = rolRepository.save(rol);

        return rolMapper.toDto(rol);
    }

    @Override
    public void delete(Long id) {

        Rol rol = buscarRol(id);

        rol.setEstadoRegistro(EstadoRegistro.INACTIVO);

        rolRepository.save(rol);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<RolDto> findById(Long id) {

        return rolRepository.findById(id)
                .map(rolMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<RolDto> findAll() {

        return rolRepository.findAll()
                .stream()
                .map(rolMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<RolDto> search(String texto) {

        return rolRepository.findByNombreContainingIgnoreCase(texto)
                .stream()
                .map(rolMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public long count() {

        return rolRepository.count();
    }

    private void validar(RolDto dto) {

        ValidationResult result = rolValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result.getMessage());
        }
    }

    private void cargarPermisos(Rol rol, RolDto dto) {

        Set<Permiso> permisos = new HashSet<>();

        if (dto.getPermisoIds() != null) {

            for (Long permisoId : dto.getPermisoIds()) {
                permisos.add(buscarPermiso(permisoId));
            }
        }

        rol.setPermisos(permisos);
    }

    private Rol buscarRol(Long id) {

        return rolRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "No existe un rol con ID: " + id));
    }

    private Permiso buscarPermiso(Long id) {

        return permisoRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "No existe un permiso con ID: " + id));
    }

    private void normalizar(RolDto dto) {

        if (dto.getNombre() != null) {
            dto.setNombre(dto.getNombre().trim());
        }

        if (dto.getDescripcion() != null) {
            dto.setDescripcion(dto.getDescripcion().trim());
        }
    }
}