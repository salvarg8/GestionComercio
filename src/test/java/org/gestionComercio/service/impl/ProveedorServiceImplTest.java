package org.gestionComercio.service.impl;

import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.ProveedorMapper;
import org.gestionComercio.repository.ProveedorRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.validation.ProveedorValidator;
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
class ProveedorServiceImplTest {

    @Mock
    private ProveedorRepository proveedorRepository;

    @Mock
    private ProveedorMapper proveedorMapper;

    @Mock
    private ProveedorValidator proveedorValidator;

    private SesionUsuario sesionUsuario;

    private ProveedorServiceImpl proveedorService;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        loguearCon(EnumSet.allOf(PermisoCodigo.class));

        proveedorService = new ProveedorServiceImpl(
                proveedorRepository, proveedorMapper, proveedorValidator, sesionUsuario);

        lenient().when(proveedorValidator.validate(any())).thenReturn(new ValidationResult());
    }

    private void loguearCon(EnumSet<PermisoCodigo> permisos) {
        sesionUsuario.iniciarSesion(new Usuario(), permisos);
    }

    private Proveedor proveedor(Long id, String razonSocial) {
        Proveedor proveedor = Proveedor.builder().razonSocial(razonSocial).build();
        proveedor.setId(id);
        return proveedor;
    }

    // ------------------------------------------------------------------
    // Alta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("save() normaliza los datos (CUIT sin guiones, opcionales vacíos en null) y guarda activo")
    void saveNormalizaYGuarda() {
        ProveedorDto dto = ProveedorDto.builder()
                .razonSocial("  Distribuidora del Centro  ")
                .cuit(" 30-71234567-1 ")
                .telefono("   ")
                .email(" ventas@dc.com ")
                .direccion("")
                .observaciones("  ")
                .build();

        Proveedor entidad = proveedor(null, "Distribuidora del Centro");
        entidad.setEstadoRegistro(EstadoRegistro.INACTIVO);

        when(proveedorMapper.toEntity(dto)).thenReturn(entidad);
        when(proveedorRepository.save(entidad)).thenReturn(entidad);

        proveedorService.save(dto);

        assertEquals("Distribuidora del Centro", dto.getRazonSocial());
        assertEquals("30712345671", dto.getCuit());
        assertNull(dto.getTelefono());
        assertEquals("ventas@dc.com", dto.getEmail());
        assertNull(dto.getDireccion());
        assertNull(dto.getObservaciones());
        assertEquals(EstadoRegistro.ACTIVO, entidad.getEstadoRegistro());
        verify(proveedorRepository).save(entidad);
    }

    @Test
    @DisplayName("save() sin CUIT lo guarda en null")
    void saveSinCuit() {
        ProveedorDto dto = ProveedorDto.builder().razonSocial("Almacén").cuit("  ").build();
        Proveedor entidad = proveedor(null, "Almacén");

        when(proveedorMapper.toEntity(dto)).thenReturn(entidad);
        when(proveedorRepository.save(entidad)).thenReturn(entidad);

        proveedorService.save(dto);

        assertNull(dto.getCuit());
    }

    @Test
    @DisplayName("save() con errores de validación no guarda")
    void saveConErroresNoGuarda() {
        ValidationResult conError = new ValidationResult();
        conError.addError("razonSocial", "La razón social o nombre es obligatoria.");
        when(proveedorValidator.validate(any())).thenReturn(conError);

        assertThrows(ValidationException.class,
                () -> proveedorService.save(new ProveedorDto()));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("save() sin PROVEEDOR_CREAR lanza AuthorizationException")
    void saveSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PROVEEDOR_VER));

        assertThrows(AuthorizationException.class,
                () -> proveedorService.save(ProveedorDto.builder().razonSocial("X").build()));

        verifyNoInteractions(proveedorRepository, proveedorValidator);
    }

    // ------------------------------------------------------------------
    // Edición
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update() actualiza un proveedor activo")
    void updateActivo() {
        ProveedorDto dto = ProveedorDto.builder().id(1L).razonSocial("Distribuidora").build();
        Proveedor existente = proveedor(1L, "Distribuidora vieja");

        when(proveedorRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));
        when(proveedorRepository.save(existente)).thenReturn(existente);

        proveedorService.update(dto);

        verify(proveedorMapper).updateEntity(dto, existente);
        verify(proveedorRepository).save(existente);
    }

    @Test
    @DisplayName("update() de un proveedor inexistente o dado de baja lanza EntityNotFoundException")
    void updateInexistente() {
        when(proveedorRepository.findByIdAndEstadoRegistro(9L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> proveedorService.update(ProveedorDto.builder().id(9L).razonSocial("X").build()));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() sin PROVEEDOR_EDITAR lanza AuthorizationException")
    void updateSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PROVEEDOR_VER, PermisoCodigo.PROVEEDOR_CREAR));

        assertThrows(AuthorizationException.class,
                () -> proveedorService.update(ProveedorDto.builder().id(1L).razonSocial("X").build()));

        verifyNoInteractions(proveedorRepository);
    }

    // ------------------------------------------------------------------
    // Baja lógica y habilitación
    // ------------------------------------------------------------------

    @Test
    @DisplayName("darDeBaja() marca el proveedor como inactivo sin borrarlo")
    void darDeBaja() {
        Proveedor existente = proveedor(1L, "Distribuidora");

        when(proveedorRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));

        proveedorService.darDeBaja(1L);

        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(proveedorRepository).save(existente);
        verify(proveedorRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete() no borra: da de baja")
    void deleteDaDeBaja() {
        Proveedor existente = proveedor(1L, "Distribuidora");

        when(proveedorRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));

        proveedorService.delete(1L);

        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(proveedorRepository, never()).delete(any());
        verify(proveedorRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("darDeBaja() de un proveedor ya dado de baja lanza EntityNotFoundException")
    void darDeBajaYaInactivo() {
        when(proveedorRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> proveedorService.darDeBaja(1L));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("darDeBaja() sin PROVEEDOR_DAR_BAJA lanza AuthorizationException")
    void darDeBajaSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PROVEEDOR_VER, PermisoCodigo.PROVEEDOR_EDITAR,
                PermisoCodigo.PROVEEDOR_HABILITAR));

        assertThrows(AuthorizationException.class, () -> proveedorService.darDeBaja(1L));
        assertThrows(AuthorizationException.class, () -> proveedorService.delete(1L));

        verifyNoInteractions(proveedorRepository);
    }

    @Test
    @DisplayName("habilitar() vuelve a activar un proveedor dado de baja")
    void habilitar() {
        Proveedor inactivo = proveedor(1L, "Distribuidora");
        inactivo.setEstadoRegistro(EstadoRegistro.INACTIVO);

        when(proveedorRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.INACTIVO))
                .thenReturn(Optional.of(inactivo));

        proveedorService.habilitar(1L);

        assertEquals(EstadoRegistro.ACTIVO, inactivo.getEstadoRegistro());
        verify(proveedorRepository).save(inactivo);
    }

    @Test
    @DisplayName("habilitar() de un proveedor que no está dado de baja lanza EntityNotFoundException")
    void habilitarNoInactivo() {
        when(proveedorRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.INACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> proveedorService.habilitar(1L));

        verify(proveedorRepository, never()).save(any());
    }

    @Test
    @DisplayName("habilitar() sin PROVEEDOR_HABILITAR lanza AuthorizationException")
    void habilitarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PROVEEDOR_VER, PermisoCodigo.PROVEEDOR_DAR_BAJA));

        assertThrows(AuthorizationException.class, () -> proveedorService.habilitar(1L));

        verifyNoInteractions(proveedorRepository);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findAll() y search() devuelven sólo los activos")
    void findAllSoloActivos() {
        Proveedor distribuidora = proveedor(1L, "Distribuidora");
        ProveedorDto dto = ProveedorDto.builder().id(1L).razonSocial("Distribuidora").build();

        when(proveedorRepository.buscar("", "", EstadoRegistro.ACTIVO))
                .thenReturn(List.of(distribuidora));
        when(proveedorMapper.toDto(distribuidora)).thenReturn(dto);

        assertEquals(List.of(dto), proveedorService.findAll());
        assertEquals(List.of(dto), proveedorService.search("   "));
    }

    @Test
    @DisplayName("buscar() por texto no busca en el CUIT")
    void buscarPorTexto() {
        proveedorService.buscar(" distri ", FiltroEstado.ACTIVOS);

        verify(proveedorRepository).buscar("distri", "", EstadoRegistro.ACTIVO);
    }

    @Test
    @DisplayName("buscar() con números busca también en el CUIT, ignorando guiones")
    void buscarPorCuit() {
        proveedorService.buscar("30-7123", FiltroEstado.TODOS);

        verify(proveedorRepository).buscar("30-7123", "307123", null);
    }

    @Test
    @DisplayName("buscar() con DADOS_DE_BAJA lista sólo los inactivos")
    void buscarDadosDeBaja() {
        proveedorService.buscar(null, FiltroEstado.DADOS_DE_BAJA);

        verify(proveedorRepository).buscar("", "", EstadoRegistro.INACTIVO);
    }

    @Test
    @DisplayName("buscar() sin PROVEEDOR_VER lanza AuthorizationException")
    void buscarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PROVEEDOR_HABILITAR));

        assertThrows(AuthorizationException.class,
                () -> proveedorService.buscar("", FiltroEstado.ACTIVOS));
    }

    @Test
    @DisplayName("findById() sólo encuentra proveedores activos")
    void findByIdActivo() {
        when(proveedorRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertEquals(Optional.empty(), proveedorService.findById(1L));
    }

    @Test
    @DisplayName("findParaSeleccion() funciona con PRODUCTO_CREAR aunque no tenga PROVEEDOR_VER")
    void findParaSeleccionConPermisoDeProducto() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_CREAR));

        when(proveedorRepository.findByEstadoRegistroOrderByRazonSocialAsc(EstadoRegistro.ACTIVO))
                .thenReturn(List.of());

        assertEquals(List.of(), proveedorService.findParaSeleccion());
    }

    @Test
    @DisplayName("findParaSeleccion() sin permisos relacionados lanza AuthorizationException")
    void findParaSeleccionSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.VENTA_VER));

        assertThrows(AuthorizationException.class, () -> proveedorService.findParaSeleccion());
    }

    @Test
    @DisplayName("count() cuenta sólo los activos")
    void countActivos() {
        when(proveedorRepository.countByEstadoRegistro(EstadoRegistro.ACTIVO)).thenReturn(4L);

        assertEquals(4L, proveedorService.count());
    }
}
