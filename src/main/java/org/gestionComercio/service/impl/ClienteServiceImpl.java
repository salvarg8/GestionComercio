package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.ClienteMapper;
import org.gestionComercio.repository.ClienteRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ClienteService;
import org.gestionComercio.validation.ClienteValidator;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;

    private final ClienteMapper clienteMapper;
    private final ClienteValidator clienteValidator;
    private final SesionUsuario sesionUsuario;

    @Override
    public ClienteDto save(ClienteDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CLIENTE_CREAR);

        normalizar(dto);
        validar(dto);

        Cliente cliente = clienteMapper.toEntity(dto);
        cliente.setId(null);
        cliente.setEstadoRegistro(EstadoRegistro.ACTIVO);

        return clienteMapper.toDto(clienteRepository.save(cliente));
    }

    @Override
    public ClienteDto update(ClienteDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CLIENTE_EDITAR);

        normalizar(dto);
        validar(dto);

        Cliente cliente = buscar(dto.getId(), EstadoRegistro.ACTIVO);

        clienteMapper.updateEntity(dto, cliente);

        return clienteMapper.toDto(clienteRepository.save(cliente));
    }

    /**
     * Se permite aunque el cliente tenga ventas: las conserva, pero ya no se
     * puede elegir para ventas nuevas.
     */
    @Override
    public void darDeBaja(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CLIENTE_DAR_BAJA);

        Cliente cliente = buscar(id, EstadoRegistro.ACTIVO);

        cliente.setEstadoRegistro(EstadoRegistro.INACTIVO);

        clienteRepository.save(cliente);
    }

    @Override
    public void habilitar(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CLIENTE_HABILITAR);

        Cliente cliente = buscar(id, EstadoRegistro.INACTIVO);

        cliente.setEstadoRegistro(EstadoRegistro.ACTIVO);

        clienteRepository.save(cliente);
    }

    /** Los clientes nunca se borran de la base: eliminar equivale a dar de baja. */
    @Override
    public void delete(Long id) {
        darDeBaja(id);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<ClienteDto> findById(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CLIENTE_VER);

        return clienteRepository.findByIdAndEstadoRegistro(id, EstadoRegistro.ACTIVO)
                .map(clienteMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ClienteDto> findAll() {
        return buscar(null, FiltroEstado.ACTIVOS);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ClienteDto> search(String texto) {
        return buscar(texto, FiltroEstado.ACTIVOS);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ClienteDto> buscar(String texto, FiltroEstado filtro) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CLIENTE_VER);

        String busqueda = texto == null ? "" : texto.trim();

        // Para el documento se ignoran guiones, puntos y espacios: "30.123" encuentra 30123456.
        String documento = busqueda.isEmpty()
                ? ""
                : busqueda.replaceAll("[\\s.\\-]", "").toUpperCase(Locale.ROOT);

        EstadoRegistro estado = switch (filtro == null ? FiltroEstado.ACTIVOS : filtro) {
            case ACTIVOS -> EstadoRegistro.ACTIVO;
            case DADOS_DE_BAJA -> EstadoRegistro.INACTIVO;
            case TODOS -> null;
        };

        return clienteRepository.buscar(busqueda, documento, estado).stream()
                .map(clienteMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public long count() {

        sesionUsuario.requerirPermiso(PermisoCodigo.CLIENTE_VER);

        return clienteRepository.countByEstadoRegistro(EstadoRegistro.ACTIVO);
    }

    private void validar(ClienteDto dto) {

        ValidationResult result = clienteValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }
    }

    /**
     * Recorta espacios, deja en null los opcionales vacíos y normaliza el documento
     * (sin guiones, puntos ni espacios; el pasaporte en mayúsculas).
     */
    private void normalizar(ClienteDto dto) {

        if (dto.getNombre() != null) {
            dto.setNombre(dto.getNombre().trim());
        }

        dto.setApellido(opcional(dto.getApellido()));
        dto.setNumeroDocumento(normalizarDocumento(dto.getTipoDocumento(), dto.getNumeroDocumento()));
        dto.setDireccion(opcional(dto.getDireccion()));
        dto.setTelefono(opcional(dto.getTelefono()));
        dto.setEmail(opcional(dto.getEmail()));
    }

    private String normalizarDocumento(TipoDocumento tipo, String numero) {

        if (numero == null) {
            return null;
        }

        String limpio = tipo == TipoDocumento.PASAPORTE
                ? numero.replaceAll("\\s", "").toUpperCase(Locale.ROOT)
                : numero.replaceAll("[\\s.\\-]", "");

        return limpio.isEmpty() ? null : limpio;
    }

    private String opcional(String valor) {

        if (valor == null) {
            return null;
        }

        String recortado = valor.trim();

        return recortado.isEmpty() ? null : recortado;
    }

    private Cliente buscar(Long id, EstadoRegistro estado) {

        return clienteRepository.findByIdAndEstadoRegistro(id, estado)
                .orElseThrow(() -> new EntityNotFoundException(
                        estado == EstadoRegistro.ACTIVO
                                ? "No existe un cliente activo con ID: " + id
                                : "No existe un cliente dado de baja con ID: " + id));
    }
}
