package org.gestionComercio.service.impl;

import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.ClienteMapper;
import org.gestionComercio.repository.ClienteRepository;
import org.gestionComercio.repository.VentaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.validation.ClienteValidator;
import org.gestionComercio.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceImplTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private ClienteMapper clienteMapper;

    @Mock
    private ClienteValidator clienteValidator;

    private SesionUsuario sesionUsuario;

    private ClienteServiceImpl clienteService;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        loguearCon(EnumSet.allOf(PermisoCodigo.class));

        clienteService = new ClienteServiceImpl(
                clienteRepository, ventaRepository, clienteMapper, clienteValidator, sesionUsuario);

        lenient().when(clienteValidator.validate(any())).thenReturn(new ValidationResult());
    }

    private void loguearCon(EnumSet<PermisoCodigo> permisos) {
        sesionUsuario.iniciarSesion(new Usuario(), permisos);
    }

    private Cliente cliente(Long id, String nombre) {
        Cliente cliente = Cliente.builder()
                .nombre(nombre)
                .apellido("Pérez")
                .tipoDocumento(TipoDocumento.DNI)
                .numeroDocumento("30123456")
                .build();
        cliente.setId(id);
        return cliente;
    }

    private ClienteDto dtoValido() {
        return ClienteDto.builder()
                .nombre("Juan")
                .apellido("Pérez")
                .tipoDocumento(TipoDocumento.DNI)
                .numeroDocumento("30123456")
                .condicionIva(CondicionIVA.CONSUMIDOR_FINAL)
                .build();
    }

    // ------------------------------------------------------------------
    // Alta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("save() normaliza los datos (documento sin puntos, opcionales vacíos en null) y guarda activo")
    void saveNormalizaYGuarda() {
        ClienteDto dto = ClienteDto.builder()
                .nombre("  Juan  ")
                .apellido("  Pérez ")
                .tipoDocumento(TipoDocumento.DNI)
                .numeroDocumento(" 30.123.456 ")
                .condicionIva(CondicionIVA.CONSUMIDOR_FINAL)
                .telefono("   ")
                .email(" juan@mail.com ")
                .direccion("")
                .build();

        Cliente entidad = cliente(null, "Juan");
        entidad.setEstadoRegistro(EstadoRegistro.INACTIVO);

        when(clienteMapper.toEntity(dto)).thenReturn(entidad);
        when(clienteRepository.save(entidad)).thenReturn(entidad);

        clienteService.save(dto);

        assertEquals("Juan", dto.getNombre());
        assertEquals("Pérez", dto.getApellido());
        assertEquals("30123456", dto.getNumeroDocumento());
        assertNull(dto.getTelefono());
        assertEquals("juan@mail.com", dto.getEmail());
        assertNull(dto.getDireccion());
        assertEquals(EstadoRegistro.ACTIVO, entidad.getEstadoRegistro());
        verify(clienteRepository).save(entidad);
    }

    @Test
    @DisplayName("save() quita los guiones del CUIT y deja el apellido vacío en null")
    void saveNormalizaCuit() {
        ClienteDto dto = ClienteDto.builder()
                .nombre("Distribuidora del Centro SA")
                .apellido("  ")
                .tipoDocumento(TipoDocumento.CUIT)
                .numeroDocumento("30-71234567-1")
                .condicionIva(CondicionIVA.RESPONSABLE_INSCRIPTO)
                .build();
        Cliente entidad = cliente(null, "Distribuidora del Centro SA");

        when(clienteMapper.toEntity(dto)).thenReturn(entidad);
        when(clienteRepository.save(entidad)).thenReturn(entidad);

        clienteService.save(dto);

        assertEquals("30712345671", dto.getNumeroDocumento());
        assertNull(dto.getApellido());
    }

    @Test
    @DisplayName("save() pasa el pasaporte a mayúsculas sin espacios")
    void saveNormalizaPasaporte() {
        ClienteDto dto = dtoValido();
        dto.setTipoDocumento(TipoDocumento.PASAPORTE);
        dto.setNumeroDocumento(" aab 123456 ");
        Cliente entidad = cliente(null, "Juan");

        when(clienteMapper.toEntity(dto)).thenReturn(entidad);
        when(clienteRepository.save(entidad)).thenReturn(entidad);

        clienteService.save(dto);

        assertEquals("AAB123456", dto.getNumeroDocumento());
    }

    @Test
    @DisplayName("save() con errores de validación no guarda")
    void saveConErroresNoGuarda() {
        ValidationResult conError = new ValidationResult();
        conError.addError("numeroDocumento", "Ya existe un cliente con ese DNI.");
        when(clienteValidator.validate(any())).thenReturn(conError);

        assertThrows(ValidationException.class, () -> clienteService.save(dtoValido()));

        verify(clienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("save() sin CLIENTE_CREAR lanza AuthorizationException")
    void saveSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.CLIENTE_VER));

        assertThrows(AuthorizationException.class, () -> clienteService.save(dtoValido()));

        verifyNoInteractions(clienteRepository, clienteValidator);
    }

    // ------------------------------------------------------------------
    // Edición
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update() actualiza un cliente activo")
    void updateActivo() {
        ClienteDto dto = dtoValido();
        dto.setId(1L);
        Cliente existente = cliente(1L, "Juan viejo");

        when(clienteRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));
        when(clienteRepository.save(existente)).thenReturn(existente);

        clienteService.update(dto);

        verify(clienteMapper).updateEntity(dto, existente);
        verify(clienteRepository).save(existente);
    }

    @Test
    @DisplayName("update() de un cliente inexistente o dado de baja lanza EntityNotFoundException")
    void updateInexistente() {
        ClienteDto dto = dtoValido();
        dto.setId(9L);

        when(clienteRepository.findByIdAndEstadoRegistro(9L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> clienteService.update(dto));

        verify(clienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() sin CLIENTE_EDITAR lanza AuthorizationException")
    void updateSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.CLIENTE_VER, PermisoCodigo.CLIENTE_CREAR));

        ClienteDto dto = dtoValido();
        dto.setId(1L);

        assertThrows(AuthorizationException.class, () -> clienteService.update(dto));

        verifyNoInteractions(clienteRepository);
    }

    // ------------------------------------------------------------------
    // Baja lógica y habilitación
    // ------------------------------------------------------------------

    @Test
    @DisplayName("darDeBaja() marca el cliente como inactivo sin borrarlo")
    void darDeBaja() {
        Cliente existente = cliente(1L, "Juan");

        when(clienteRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));

        clienteService.darDeBaja(1L);

        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(clienteRepository).save(existente);
        verify(clienteRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete() no borra: da de baja")
    void deleteDaDeBaja() {
        Cliente existente = cliente(1L, "Juan");

        when(clienteRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));

        clienteService.delete(1L);

        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(clienteRepository, never()).delete(any());
        verify(clienteRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("darDeBaja() de un cliente ya dado de baja lanza EntityNotFoundException")
    void darDeBajaYaInactivo() {
        when(clienteRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> clienteService.darDeBaja(1L));

        verify(clienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("darDeBaja() sin CLIENTE_DAR_BAJA lanza AuthorizationException")
    void darDeBajaSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.CLIENTE_VER, PermisoCodigo.CLIENTE_EDITAR,
                PermisoCodigo.CLIENTE_HABILITAR, PermisoCodigo.CLIENTE_ELIMINAR));

        assertThrows(AuthorizationException.class, () -> clienteService.darDeBaja(1L));
        assertThrows(AuthorizationException.class, () -> clienteService.delete(1L));

        verifyNoInteractions(clienteRepository);
    }

    @Test
    @DisplayName("habilitar() vuelve a activar un cliente dado de baja")
    void habilitar() {
        Cliente inactivo = cliente(1L, "Juan");
        inactivo.setEstadoRegistro(EstadoRegistro.INACTIVO);

        when(clienteRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.INACTIVO))
                .thenReturn(Optional.of(inactivo));

        clienteService.habilitar(1L);

        assertEquals(EstadoRegistro.ACTIVO, inactivo.getEstadoRegistro());
        verify(clienteRepository).save(inactivo);
    }

    @Test
    @DisplayName("habilitar() de un cliente que no está dado de baja lanza EntityNotFoundException")
    void habilitarNoInactivo() {
        when(clienteRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.INACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> clienteService.habilitar(1L));

        verify(clienteRepository, never()).save(any());
    }

    @Test
    @DisplayName("habilitar() sin CLIENTE_HABILITAR lanza AuthorizationException")
    void habilitarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.CLIENTE_VER, PermisoCodigo.CLIENTE_DAR_BAJA));

        assertThrows(AuthorizationException.class, () -> clienteService.habilitar(1L));

        verifyNoInteractions(clienteRepository);
    }

    // ------------------------------------------------------------------
    // Eliminación física
    // ------------------------------------------------------------------

    @Test
    @DisplayName("eliminar() borra de la base un cliente sin ventas")
    void eliminarSinVentas() {
        Cliente existente = cliente(1L, "Juan");

        when(clienteRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(ventaRepository.existsByClienteId(1L)).thenReturn(false);

        clienteService.eliminar(1L);

        verify(clienteRepository).delete(existente);
    }

    @Test
    @DisplayName("eliminar() de un cliente con ventas lanza BusinessException y no borra")
    void eliminarConVentas() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente(1L, "Juan")));
        when(ventaRepository.existsByClienteId(1L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> clienteService.eliminar(1L));

        verify(clienteRepository, never()).delete(any());
    }

    @Test
    @DisplayName("eliminar() de un cliente inexistente lanza EntityNotFoundException")
    void eliminarInexistente() {
        when(clienteRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> clienteService.eliminar(9L));

        verify(clienteRepository, never()).delete(any());
    }

    @Test
    @DisplayName("eliminar() sin CLIENTE_ELIMINAR lanza AuthorizationException")
    void eliminarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.CLIENTE_VER, PermisoCodigo.CLIENTE_DAR_BAJA));

        assertThrows(AuthorizationException.class, () -> clienteService.eliminar(1L));

        verifyNoInteractions(clienteRepository, ventaRepository);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findAll() y search() devuelven sólo los activos")
    void findAllSoloActivos() {
        Cliente juan = cliente(1L, "Juan");
        ClienteDto dto = ClienteDto.builder().id(1L).nombre("Juan").build();

        when(clienteRepository.buscar("", "", EstadoRegistro.ACTIVO)).thenReturn(List.of(juan));
        when(clienteMapper.toDto(juan)).thenReturn(dto);

        assertEquals(List.of(dto), clienteService.findAll());
        assertEquals(List.of(dto), clienteService.search("   "));
    }

    @Test
    @DisplayName("buscar() busca el documento ignorando puntos y guiones")
    void buscarPorDocumento() {
        clienteService.buscar("30.123", FiltroEstado.TODOS);

        verify(clienteRepository).buscar("30.123", "30123", null);
    }

    @Test
    @DisplayName("buscar() con DADOS_DE_BAJA lista sólo los inactivos")
    void buscarDadosDeBaja() {
        clienteService.buscar(null, FiltroEstado.DADOS_DE_BAJA);

        verify(clienteRepository).buscar("", "", EstadoRegistro.INACTIVO);
    }

    @Test
    @DisplayName("buscar() sin CLIENTE_VER lanza AuthorizationException")
    void buscarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.CLIENTE_CREAR));

        assertThrows(AuthorizationException.class,
                () -> clienteService.buscar("", FiltroEstado.ACTIVOS));
    }

    @Test
    @DisplayName("findById() sólo encuentra clientes activos")
    void findByIdActivo() {
        when(clienteRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertEquals(Optional.empty(), clienteService.findById(1L));
    }

    @Test
    @DisplayName("count() cuenta sólo los activos")
    void countActivos() {
        when(clienteRepository.countByEstadoRegistro(EstadoRegistro.ACTIVO)).thenReturn(7L);

        assertEquals(7L, clienteService.count());
    }
}
