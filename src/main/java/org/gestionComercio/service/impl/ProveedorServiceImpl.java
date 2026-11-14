package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.ProveedorMapper;
import org.gestionComercio.repository.ProveedorRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ProveedorService;
import org.gestionComercio.util.CuitUtils;
import org.gestionComercio.validation.ProveedorValidator;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProveedorServiceImpl implements ProveedorService {

    private final ProveedorRepository proveedorRepository;

    private final ProveedorMapper proveedorMapper;
    private final ProveedorValidator proveedorValidator;
    private final SesionUsuario sesionUsuario;

    @Override
    public ProveedorDto save(ProveedorDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PROVEEDOR_CREAR);

        normalizar(dto);
        validar(dto);

        Proveedor proveedor = proveedorMapper.toEntity(dto);
        proveedor.setId(null);
        proveedor.setEstadoRegistro(EstadoRegistro.ACTIVO);

        return proveedorMapper.toDto(proveedorRepository.save(proveedor));
    }

    @Override
    public ProveedorDto update(ProveedorDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PROVEEDOR_EDITAR);

        normalizar(dto);
        validar(dto);

        Proveedor proveedor = buscar(dto.getId(), EstadoRegistro.ACTIVO);

        proveedorMapper.updateEntity(dto, proveedor);

        return proveedorMapper.toDto(proveedorRepository.save(proveedor));
    }

    /**
     * Se permite aunque el proveedor tenga productos o compras: los conservan,
     * pero ya no se puede elegir para registros nuevos.
     */
    @Override
    public void darDeBaja(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PROVEEDOR_DAR_BAJA);

        Proveedor proveedor = buscar(id, EstadoRegistro.ACTIVO);

        proveedor.setEstadoRegistro(EstadoRegistro.INACTIVO);

        proveedorRepository.save(proveedor);
    }

    @Override
    public void habilitar(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PROVEEDOR_HABILITAR);

        Proveedor proveedor = buscar(id, EstadoRegistro.INACTIVO);

        proveedor.setEstadoRegistro(EstadoRegistro.ACTIVO);

        proveedorRepository.save(proveedor);
    }

    @Override
    public void delete(Long id) {
        darDeBaja(id);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<ProveedorDto> findById(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PROVEEDOR_VER);

        return proveedorRepository.findByIdAndEstadoRegistro(id, EstadoRegistro.ACTIVO)
                .map(proveedorMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProveedorDto> findAll() {
        return buscar(null, FiltroEstado.ACTIVOS);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProveedorDto> search(String texto) {
        return buscar(texto, FiltroEstado.ACTIVOS);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProveedorDto> buscar(String texto, FiltroEstado filtro) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PROVEEDOR_VER);

        String busqueda = texto == null ? "" : texto.trim();

        // Para el CUIT se ignoran guiones y espacios: "20-1234" encuentra 20123456789.
        String digitos = busqueda.matches("[\\d\\s.\\-]+")
                ? busqueda.replaceAll("\\D", "")
                : "";

        EstadoRegistro estado = switch (filtro == null ? FiltroEstado.ACTIVOS : filtro) {
            case ACTIVOS -> EstadoRegistro.ACTIVO;
            case DADOS_DE_BAJA -> EstadoRegistro.INACTIVO;
            case TODOS -> null;
        };

        return proveedorRepository.buscar(busqueda, digitos, estado).stream()
                .map(proveedorMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public long count() {

        sesionUsuario.requerirPermiso(PermisoCodigo.PROVEEDOR_VER);

        return proveedorRepository.countByEstadoRegistro(EstadoRegistro.ACTIVO);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProveedorDto> findParaSeleccion() {

        sesionUsuario.requerirAlgunPermiso(
                PermisoCodigo.PROVEEDOR_VER,
                PermisoCodigo.PRODUCTO_CREAR,
                PermisoCodigo.PRODUCTO_EDITAR);

        return proveedorRepository.findByEstadoRegistroOrderByRazonSocialAsc(EstadoRegistro.ACTIVO)
                .stream()
                .map(proveedorMapper::toDto)
                .toList();
    }

    private void validar(ProveedorDto dto) {

        ValidationResult result = proveedorValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }
    }

    /** Recorta espacios, deja en null los opcionales vacíos y saca los guiones del CUIT. */
    private void normalizar(ProveedorDto dto) {

        if (dto.getRazonSocial() != null) {
            dto.setRazonSocial(dto.getRazonSocial().trim());
        }

        dto.setCuit(CuitUtils.normalizar(dto.getCuit()));
        dto.setTelefono(opcional(dto.getTelefono()));
        dto.setEmail(opcional(dto.getEmail()));
        dto.setDireccion(opcional(dto.getDireccion()));
        dto.setObservaciones(opcional(dto.getObservaciones()));
    }

    private String opcional(String valor) {

        if (valor == null) {
            return null;
        }

        String recortado = valor.trim();

        return recortado.isEmpty() ? null : recortado;
    }

    private Proveedor buscar(Long id, EstadoRegistro estado) {

        return proveedorRepository.findByIdAndEstadoRegistro(id, estado)
                .orElseThrow(() -> new EntityNotFoundException(
                        estado == EstadoRegistro.ACTIVO
                                ? "No existe un proveedor activo con ID: " + id
                                : "No existe un proveedor dado de baja con ID: " + id));
    }
}
