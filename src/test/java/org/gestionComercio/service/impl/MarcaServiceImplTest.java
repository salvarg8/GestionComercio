package org.gestionComercio.service.impl;

import org.gestionComercio.dto.marca.MarcaDto;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.MarcaMapper;
import org.gestionComercio.repository.MarcaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.validation.MarcaValidator;
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
class MarcaServiceImplTest {

    @Mock
    private MarcaRepository marcaRepository;

    @Mock
    private MarcaMapper marcaMapper;

    @Mock
    private MarcaValidator marcaValidator;

    private SesionUsuario sesionUsuario;

    private MarcaServiceImpl marcaService;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        loguearCon(EnumSet.allOf(PermisoCodigo.class));

        marcaService = new MarcaServiceImpl(
                marcaRepository, marcaMapper, marcaValidator, sesionUsuario);

        lenient().when(marcaValidator.validate(any())).thenReturn(new ValidationResult());
    }

    private void loguearCon(EnumSet<PermisoCodigo> permisos) {
        sesionUsuario.iniciarSesion(new Usuario(), permisos);
    }

    private Marca marca(Long id, String nombre) {
        Marca marca = Marca.builder().nombre(nombre).build();
        marca.setId(id);
        return marca;
    }

    // ------------------------------------------------------------------
    // Alta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("save() normaliza nombre y descripción y guarda")
    void saveNormalizaYGuarda() {
        MarcaDto dto = MarcaDto.builder()
                .nombre("  Bebidas  ")
                .descripcion("   ")
                .build();

        Marca entidad = marca(null, "Bebidas");

        when(marcaMapper.toEntity(dto)).thenReturn(entidad);
        when(marcaRepository.save(entidad)).thenReturn(entidad);

        marcaService.save(dto);

        assertEquals("Bebidas", dto.getNombre());
        assertNull(dto.getDescripcion());
        assertEquals(EstadoRegistro.ACTIVO, entidad.getEstadoRegistro());
        verify(marcaRepository).save(entidad);
    }

    @Test
    @DisplayName("save() con errores de validación no guarda")
    void saveConErroresNoGuarda() {
        ValidationResult conError = new ValidationResult();
        conError.addError("nombre", "El nombre es obligatorio.");
        when(marcaValidator.validate(any())).thenReturn(conError);

        assertThrows(ValidationException.class,
                () -> marcaService.save(new MarcaDto()));

        verify(marcaRepository, never()).save(any());
    }

    @Test
    @DisplayName("save() sin MARCA_CREAR lanza AuthorizationException")
    void saveSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.MARCA_VER));

        assertThrows(AuthorizationException.class,
                () -> marcaService.save(MarcaDto.builder().nombre("X").build()));

        verifyNoInteractions(marcaRepository, marcaValidator);
    }

    // ------------------------------------------------------------------
    // Edición
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update() actualiza una marca activa")
    void updateActiva() {
        MarcaDto dto = MarcaDto.builder().id(1L).nombre("Lácteos").build();
        Marca existente = marca(1L, "Lacteos");

        when(marcaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));
        when(marcaRepository.save(existente)).thenReturn(existente);

        marcaService.update(dto);

        verify(marcaMapper).updateEntity(dto, existente);
        verify(marcaRepository).save(existente);
    }

    @Test
    @DisplayName("update() de una marca inexistente o dada de baja lanza EntityNotFoundException")
    void updateInexistente() {
        when(marcaRepository.findByIdAndEstadoRegistro(9L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> marcaService.update(MarcaDto.builder().id(9L).nombre("X").build()));

        verify(marcaRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() sin MARCA_EDITAR lanza AuthorizationException")
    void updateSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.MARCA_VER, PermisoCodigo.MARCA_CREAR));

        assertThrows(AuthorizationException.class,
                () -> marcaService.update(MarcaDto.builder().id(1L).nombre("X").build()));

        verifyNoInteractions(marcaRepository);
    }

    // ------------------------------------------------------------------
    // Baja y habilitación
    // ------------------------------------------------------------------

    @Test
    @DisplayName("darDeBaja() marca la marca como inactiva, aunque tenga productos")
    void darDeBaja() {
        Marca existente = marca(1L, "Bebidas");

        when(marcaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));

        marcaService.darDeBaja(1L);

        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(marcaRepository).save(existente);
        verify(marcaRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete() no borra: da de baja")
    void deleteDaDeBaja() {
        Marca existente = marca(1L, "Bebidas");

        when(marcaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));

        marcaService.delete(1L);

        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(marcaRepository, never()).delete(any());
        verify(marcaRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("darDeBaja() de una marca ya dada de baja lanza EntityNotFoundException")
    void darDeBajaYaInactiva() {
        when(marcaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> marcaService.darDeBaja(1L));

        verify(marcaRepository, never()).save(any());
    }

    @Test
    @DisplayName("darDeBaja() sin MARCA_DAR_BAJA lanza AuthorizationException")
    void darDeBajaSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.MARCA_VER, PermisoCodigo.MARCA_EDITAR,
                PermisoCodigo.MARCA_HABILITAR));

        assertThrows(AuthorizationException.class, () -> marcaService.darDeBaja(1L));
        assertThrows(AuthorizationException.class, () -> marcaService.delete(1L));

        verifyNoInteractions(marcaRepository);
    }

    @Test
    @DisplayName("habilitar() vuelve a activar una marca dada de baja")
    void habilitar() {
        Marca inactiva = marca(1L, "Bebidas");
        inactiva.setEstadoRegistro(EstadoRegistro.INACTIVO);

        when(marcaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.INACTIVO))
                .thenReturn(Optional.of(inactiva));

        marcaService.habilitar(1L);

        assertEquals(EstadoRegistro.ACTIVO, inactiva.getEstadoRegistro());
        verify(marcaRepository).save(inactiva);
    }

    @Test
    @DisplayName("habilitar() de una marca que no está dada de baja lanza EntityNotFoundException")
    void habilitarNoInactiva() {
        when(marcaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.INACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> marcaService.habilitar(1L));

        verify(marcaRepository, never()).save(any());
    }

    @Test
    @DisplayName("habilitar() sin MARCA_HABILITAR lanza AuthorizationException")
    void habilitarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.MARCA_VER, PermisoCodigo.MARCA_DAR_BAJA));

        assertThrows(AuthorizationException.class, () -> marcaService.habilitar(1L));

        verifyNoInteractions(marcaRepository);
    }

    // ------------------------------------------------------------------
    // Búsqueda por estado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("buscar() con DADOS_DE_BAJA lista solo las inactivas")
    void buscarDadasDeBaja() {
        when(marcaRepository.findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.INACTIVO))
                .thenReturn(List.of());

        marcaService.buscar("", FiltroEstado.DADOS_DE_BAJA);

        verify(marcaRepository).findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.INACTIVO);
    }

    @Test
    @DisplayName("buscar() con TODOS no filtra por estado")
    void buscarTodas() {
        when(marcaRepository.findAllByOrderByNombreAsc()).thenReturn(List.of());
        when(marcaRepository.findByNombreContainingIgnoreCaseOrderByNombreAsc("beb"))
                .thenReturn(List.of());

        marcaService.buscar(null, FiltroEstado.TODOS);
        marcaService.buscar(" beb ", FiltroEstado.TODOS);

        verify(marcaRepository).findAllByOrderByNombreAsc();
        verify(marcaRepository).findByNombreContainingIgnoreCaseOrderByNombreAsc("beb");
    }

    @Test
    @DisplayName("buscar() sin MARCA_VER lanza AuthorizationException")
    void buscarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.MARCA_HABILITAR));

        assertThrows(AuthorizationException.class,
                () -> marcaService.buscar("", FiltroEstado.DADOS_DE_BAJA));
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findAll() devuelve solo las activas")
    void findAllSoloActivas() {
        Marca bebidas = marca(1L, "Bebidas");
        MarcaDto dto = MarcaDto.builder().id(1L).nombre("Bebidas").build();

        when(marcaRepository.findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.ACTIVO))
                .thenReturn(List.of(bebidas));
        when(marcaMapper.toDto(bebidas)).thenReturn(dto);

        assertEquals(List.of(dto), marcaService.findAll());
    }

    @Test
    @DisplayName("search() con texto vacío lista todas las activas")
    void searchVacio() {
        when(marcaRepository.findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.ACTIVO))
                .thenReturn(List.of());

        marcaService.search("   ");

        verify(marcaRepository).findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.ACTIVO);
    }

    @Test
    @DisplayName("search() filtra por nombre entre las activas")
    void searchPorNombre() {
        when(marcaRepository.findByNombreContainingIgnoreCaseAndEstadoRegistroOrderByNombreAsc(
                "beb", EstadoRegistro.ACTIVO)).thenReturn(List.of());

        marcaService.search(" beb ");

        verify(marcaRepository)
                .findByNombreContainingIgnoreCaseAndEstadoRegistroOrderByNombreAsc(
                        "beb", EstadoRegistro.ACTIVO);
    }

    @Test
    @DisplayName("findAll() sin MARCA_VER lanza AuthorizationException")
    void findAllSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_CREAR));

        assertThrows(AuthorizationException.class, () -> marcaService.findAll());
    }

    @Test
    @DisplayName("findParaSeleccion() funciona con PRODUCTO_CREAR aunque no tenga MARCA_VER")
    void findParaSeleccionConPermisoDeProducto() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_CREAR));

        when(marcaRepository.findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.ACTIVO))
                .thenReturn(List.of());

        assertEquals(List.of(), marcaService.findParaSeleccion());
    }

    @Test
    @DisplayName("findParaSeleccion() sin permisos relacionados lanza AuthorizationException")
    void findParaSeleccionSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.VENTA_VER));

        assertThrows(AuthorizationException.class, () -> marcaService.findParaSeleccion());
    }

    @Test
    @DisplayName("count() cuenta solo las activas")
    void countActivas() {
        when(marcaRepository.countByEstadoRegistro(EstadoRegistro.ACTIVO)).thenReturn(3L);

        assertEquals(3L, marcaService.count());
    }
}
