package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.categoria.CategoriaDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.CategoriaMapper;
import org.gestionComercio.repository.CategoriaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.CategoriaService;
import org.gestionComercio.validation.CategoriaValidator;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    private final CategoriaMapper categoriaMapper;
    private final CategoriaValidator categoriaValidator;
    private final SesionUsuario sesionUsuario;

    @Override
    public CategoriaDto save(CategoriaDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CATEGORIA_CREAR);

        normalizar(dto);
        validar(dto);

        Categoria categoria = categoriaMapper.toEntity(dto);
        categoria.setId(null);

        return categoriaMapper.toDto(categoriaRepository.save(categoria));
    }

    @Override
    public CategoriaDto update(CategoriaDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CATEGORIA_EDITAR);

        normalizar(dto);
        validar(dto);

        Categoria categoria = buscarActiva(dto.getId());

        categoriaMapper.updateEntity(dto, categoria);

        return categoriaMapper.toDto(categoriaRepository.save(categoria));
    }

    @Override
    public void delete(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CATEGORIA_ELIMINAR);

        Categoria categoria = buscarActiva(id);

        if (productoRepository.existsByCategoriaIdAndEstadoRegistro(id, EstadoRegistro.ACTIVO)) {
            throw new BusinessException(
                    "No se puede eliminar la categoría \"" + categoria.getNombre()
                            + "\" porque tiene productos asociados.");
        }

        categoria.setEstadoRegistro(EstadoRegistro.INACTIVO);

        categoriaRepository.save(categoria);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<CategoriaDto> findById(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CATEGORIA_VER);

        return categoriaRepository.findByIdAndEstadoRegistro(id, EstadoRegistro.ACTIVO)
                .map(categoriaMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoriaDto> findAll() {

        sesionUsuario.requerirPermiso(PermisoCodigo.CATEGORIA_VER);

        return listarActivas();
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoriaDto> search(String texto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CATEGORIA_VER);

        if (texto == null || texto.isBlank()) {
            return listarActivas();
        }

        return categoriaRepository
                .findByNombreContainingIgnoreCaseAndEstadoRegistroOrderByNombreAsc(
                        texto.trim(), EstadoRegistro.ACTIVO)
                .stream()
                .map(categoriaMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public long count() {

        sesionUsuario.requerirPermiso(PermisoCodigo.CATEGORIA_VER);

        return categoriaRepository.countByEstadoRegistro(EstadoRegistro.ACTIVO);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CategoriaDto> findParaSeleccion() {

        sesionUsuario.requerirAlgunPermiso(
                PermisoCodigo.CATEGORIA_VER,
                PermisoCodigo.PRODUCTO_CREAR,
                PermisoCodigo.PRODUCTO_EDITAR);

        return listarActivas();
    }

    private List<CategoriaDto> listarActivas() {

        return categoriaRepository
                .findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.ACTIVO)
                .stream()
                .map(categoriaMapper::toDto)
                .toList();
    }

    private void validar(CategoriaDto dto) {

        ValidationResult result = categoriaValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }
    }

    private void normalizar(CategoriaDto dto) {

        if (dto.getNombre() != null) {
            dto.setNombre(dto.getNombre().trim());
        }

        if (dto.getDescripcion() != null) {
            String descripcion = dto.getDescripcion().trim();
            dto.setDescripcion(descripcion.isEmpty() ? null : descripcion);
        }
    }

    private Categoria buscarActiva(Long id) {

        return categoriaRepository.findByIdAndEstadoRegistro(id, EstadoRegistro.ACTIVO)
                .orElseThrow(() ->
                        new EntityNotFoundException("No existe una categoría con ID: " + id));
    }
}
