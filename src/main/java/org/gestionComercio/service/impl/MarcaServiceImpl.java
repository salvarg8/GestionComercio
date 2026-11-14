package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.marca.MarcaDto;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.MarcaMapper;
import org.gestionComercio.repository.MarcaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.MarcaService;
import org.gestionComercio.validation.MarcaValidator;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class MarcaServiceImpl implements MarcaService {

    private final MarcaRepository marcaRepository;

    private final MarcaMapper marcaMapper;
    private final MarcaValidator marcaValidator;
    private final SesionUsuario sesionUsuario;

    @Override
    public MarcaDto save(MarcaDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.MARCA_CREAR);

        normalizar(dto);
        validar(dto);

        Marca marca = marcaMapper.toEntity(dto);
        marca.setId(null);
        marca.setEstadoRegistro(EstadoRegistro.ACTIVO);

        return marcaMapper.toDto(marcaRepository.save(marca));
    }

    @Override
    public MarcaDto update(MarcaDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.MARCA_EDITAR);

        normalizar(dto);
        validar(dto);

        Marca marca = buscar(dto.getId(), EstadoRegistro.ACTIVO);

        marcaMapper.updateEntity(dto, marca);

        return marcaMapper.toDto(marcaRepository.save(marca));
    }

    /**
     * Se permite aunque la marca tenga productos: los productos la conservan,
     * pero ya no se puede elegir para productos nuevos.
     */
    @Override
    public void darDeBaja(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.MARCA_DAR_BAJA);

        Marca marca = buscar(id, EstadoRegistro.ACTIVO);

        marca.setEstadoRegistro(EstadoRegistro.INACTIVO);

        marcaRepository.save(marca);
    }

    @Override
    public void habilitar(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.MARCA_HABILITAR);

        Marca marca = buscar(id, EstadoRegistro.INACTIVO);

        marca.setEstadoRegistro(EstadoRegistro.ACTIVO);

        marcaRepository.save(marca);
    }

    @Override
    public void delete(Long id) {
        darDeBaja(id);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<MarcaDto> findById(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.MARCA_VER);

        return marcaRepository.findByIdAndEstadoRegistro(id, EstadoRegistro.ACTIVO)
                .map(marcaMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MarcaDto> findAll() {
        return buscar(null, FiltroEstado.ACTIVOS);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MarcaDto> search(String texto) {
        return buscar(texto, FiltroEstado.ACTIVOS);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MarcaDto> buscar(String texto, FiltroEstado filtro) {

        sesionUsuario.requerirPermiso(PermisoCodigo.MARCA_VER);

        String nombre = texto == null ? "" : texto.trim();
        FiltroEstado estado = filtro == null ? FiltroEstado.ACTIVOS : filtro;

        List<Marca> marcas = switch (estado) {
            case ACTIVOS -> porEstado(nombre, EstadoRegistro.ACTIVO);
            case DADOS_DE_BAJA -> porEstado(nombre, EstadoRegistro.INACTIVO);
            case TODOS -> nombre.isEmpty()
                    ? marcaRepository.findAllByOrderByNombreAsc()
                    : marcaRepository.findByNombreContainingIgnoreCaseOrderByNombreAsc(nombre);
        };

        return marcas.stream()
                .map(marcaMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public long count() {

        sesionUsuario.requerirPermiso(PermisoCodigo.MARCA_VER);

        return marcaRepository.countByEstadoRegistro(EstadoRegistro.ACTIVO);
    }

    @Transactional(readOnly = true)
    @Override
    public List<MarcaDto> findParaSeleccion() {

        sesionUsuario.requerirAlgunPermiso(
                PermisoCodigo.MARCA_VER,
                PermisoCodigo.PRODUCTO_CREAR,
                PermisoCodigo.PRODUCTO_EDITAR);

        return porEstado("", EstadoRegistro.ACTIVO).stream()
                .map(marcaMapper::toDto)
                .toList();
    }

    private List<Marca> porEstado(String nombre, EstadoRegistro estado) {

        return nombre.isEmpty()
                ? marcaRepository.findByEstadoRegistroOrderByNombreAsc(estado)
                : marcaRepository.findByNombreContainingIgnoreCaseAndEstadoRegistroOrderByNombreAsc(
                        nombre, estado);
    }

    private void validar(MarcaDto dto) {

        ValidationResult result = marcaValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }
    }

    private void normalizar(MarcaDto dto) {

        if (dto.getNombre() != null) {
            dto.setNombre(dto.getNombre().trim());
        }

        if (dto.getDescripcion() != null) {
            String descripcion = dto.getDescripcion().trim();
            dto.setDescripcion(descripcion.isEmpty() ? null : descripcion);
        }
    }

    private Marca buscar(Long id, EstadoRegistro estado) {

        return marcaRepository.findByIdAndEstadoRegistro(id, estado)
                .orElseThrow(() -> new EntityNotFoundException(
                        estado == EstadoRegistro.ACTIVO
                                ? "No existe una marca activa con ID: " + id
                                : "No existe una marca dada de baja con ID: " + id));
    }
}
