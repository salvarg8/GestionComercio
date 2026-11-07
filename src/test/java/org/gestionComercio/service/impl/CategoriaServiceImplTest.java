package org.gestionComercio.service.impl;

import org.gestionComercio.dto.categoria.CategoriaDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.CategoriaMapper;
import org.gestionComercio.repository.CategoriaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.validation.CategoriaValidator;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceImplTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaMapper categoriaMapper;

    @Mock
    private CategoriaValidator categoriaValidator;

    private SesionUsuario sesionUsuario;

    private CategoriaServiceImpl categoriaService;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        loguearCon(EnumSet.allOf(PermisoCodigo.class));

        categoriaService = new CategoriaServiceImpl(
                categoriaRepository, productoRepository,
                categoriaMapper, categoriaValidator, sesionUsuario);

        lenient().when(categoriaValidator.validate(any())).thenReturn(new ValidationResult());
    }

    private void loguearCon(EnumSet<PermisoCodigo> permisos) {
        sesionUsuario.iniciarSesion(new Usuario(), permisos);
    }

    private Categoria categoria(Long id, String nombre) {
        Categoria categoria = Categoria.builder().nombre(nombre).build();
        categoria.setId(id);
        return categoria;
    }

    // ------------------------------------------------------------------
    // Alta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("save() normaliza nombre y descripción y guarda")
    void saveNormalizaYGuarda() {
        CategoriaDto dto = CategoriaDto.builder()
                .nombre("  Bebidas  ")
                .descripcion("   ")
                .build();

        Categoria entidad = categoria(null, "Bebidas");

        when(categoriaMapper.toEntity(dto)).thenReturn(entidad);
        when(categoriaRepository.save(entidad)).thenReturn(entidad);

        categoriaService.save(dto);

        assertEquals("Bebidas", dto.getNombre());
        assertNull(dto.getDescripcion());
        verify(categoriaRepository).save(entidad);
    }

    @Test
    @DisplayName("save() con errores de validación no guarda")
    void saveConErroresNoGuarda() {
        ValidationResult conError = new ValidationResult();
        conError.addError("nombre", "El nombre es obligatorio.");
        when(categoriaValidator.validate(any())).thenReturn(conError);

        assertThrows(ValidationException.class,
                () -> categoriaService.save(new CategoriaDto()));

        verify(categoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("save() sin CATEGORIA_CREAR lanza AuthorizationException")
    void saveSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.CATEGORIA_VER));

        assertThrows(AuthorizationException.class,
                () -> categoriaService.save(CategoriaDto.builder().nombre("X").build()));

        verifyNoInteractions(categoriaRepository, categoriaValidator);
    }

    // ------------------------------------------------------------------
    // Edición
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update() actualiza una categoría activa")
    void updateActiva() {
        CategoriaDto dto = CategoriaDto.builder().id(1L).nombre("Lácteos").build();
        Categoria existente = categoria(1L, "Lacteos");

        when(categoriaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));
        when(categoriaRepository.save(existente)).thenReturn(existente);

        categoriaService.update(dto);

        verify(categoriaMapper).updateEntity(dto, existente);
        verify(categoriaRepository).save(existente);
    }

    @Test
    @DisplayName("update() de una categoría inexistente o eliminada lanza EntityNotFoundException")
    void updateInexistente() {
        when(categoriaRepository.findByIdAndEstadoRegistro(9L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> categoriaService.update(CategoriaDto.builder().id(9L).nombre("X").build()));

        verify(categoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() sin CATEGORIA_EDITAR lanza AuthorizationException")
    void updateSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.CATEGORIA_VER, PermisoCodigo.CATEGORIA_CREAR));

        assertThrows(AuthorizationException.class,
                () -> categoriaService.update(CategoriaDto.builder().id(1L).nombre("X").build()));

        verifyNoInteractions(categoriaRepository);
    }

    // ------------------------------------------------------------------
    // Baja
    // ------------------------------------------------------------------

    @Test
    @DisplayName("delete() desactiva lógicamente una categoría sin productos")
    void deleteLogico() {
        Categoria existente = categoria(1L, "Bebidas");

        when(categoriaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));
        when(productoRepository.existsByCategoriaIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(false);

        categoriaService.delete(1L);

        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(categoriaRepository).save(existente);
    }

    @Test
    @DisplayName("delete() no permite eliminar una categoría con productos activos")
    void deleteConProductos() {
        Categoria existente = categoria(1L, "Bebidas");

        when(categoriaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));
        when(productoRepository.existsByCategoriaIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoriaService.delete(1L));

        assertTrue(ex.getMessage().contains("productos asociados"));
        assertEquals(EstadoRegistro.ACTIVO, existente.getEstadoRegistro());
        verify(categoriaRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete() sin CATEGORIA_ELIMINAR lanza AuthorizationException")
    void deleteSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.CATEGORIA_VER, PermisoCodigo.CATEGORIA_EDITAR));

        assertThrows(AuthorizationException.class, () -> categoriaService.delete(1L));

        verifyNoInteractions(categoriaRepository, productoRepository);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findAll() devuelve solo las activas")
    void findAllSoloActivas() {
        Categoria bebidas = categoria(1L, "Bebidas");
        CategoriaDto dto = CategoriaDto.builder().id(1L).nombre("Bebidas").build();

        when(categoriaRepository.findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.ACTIVO))
                .thenReturn(List.of(bebidas));
        when(categoriaMapper.toDto(bebidas)).thenReturn(dto);

        assertEquals(List.of(dto), categoriaService.findAll());
    }

    @Test
    @DisplayName("search() con texto vacío lista todas las activas")
    void searchVacio() {
        when(categoriaRepository.findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.ACTIVO))
                .thenReturn(List.of());

        categoriaService.search("   ");

        verify(categoriaRepository).findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.ACTIVO);
    }

    @Test
    @DisplayName("search() filtra por nombre entre las activas")
    void searchPorNombre() {
        when(categoriaRepository.findByNombreContainingIgnoreCaseAndEstadoRegistroOrderByNombreAsc(
                "beb", EstadoRegistro.ACTIVO)).thenReturn(List.of());

        categoriaService.search(" beb ");

        verify(categoriaRepository)
                .findByNombreContainingIgnoreCaseAndEstadoRegistroOrderByNombreAsc(
                        "beb", EstadoRegistro.ACTIVO);
    }

    @Test
    @DisplayName("findAll() sin CATEGORIA_VER lanza AuthorizationException")
    void findAllSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_CREAR));

        assertThrows(AuthorizationException.class, () -> categoriaService.findAll());
    }

    @Test
    @DisplayName("findParaSeleccion() funciona con PRODUCTO_CREAR aunque no tenga CATEGORIA_VER")
    void findParaSeleccionConPermisoDeProducto() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_CREAR));

        when(categoriaRepository.findByEstadoRegistroOrderByNombreAsc(EstadoRegistro.ACTIVO))
                .thenReturn(List.of());

        assertEquals(List.of(), categoriaService.findParaSeleccion());
    }

    @Test
    @DisplayName("findParaSeleccion() sin permisos relacionados lanza AuthorizationException")
    void findParaSeleccionSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.VENTA_VER));

        assertThrows(AuthorizationException.class, () -> categoriaService.findParaSeleccion());
    }

    @Test
    @DisplayName("count() cuenta solo las activas")
    void countActivas() {
        when(categoriaRepository.countByEstadoRegistro(EstadoRegistro.ACTIVO)).thenReturn(3L);

        assertEquals(3L, categoriaService.count());
    }
}
