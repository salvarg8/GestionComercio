package org.gestionComercio.service.impl;

import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ConcurrencyException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.ProductoMapper;
import org.gestionComercio.repository.CategoriaRepository;
import org.gestionComercio.repository.MarcaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.ProveedorRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.validation.ProductoValidator;
import org.gestionComercio.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceImplTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private MarcaRepository marcaRepository;

    @Mock
    private ProveedorRepository proveedorRepository;

    @Mock
    private ProductoMapper productoMapper;

    @Mock
    private ProductoValidator productoValidator;

    private SesionUsuario sesionUsuario;

    private ProductoServiceImpl productoService;

    private Categoria categoria;
    private Marca marca;
    private Proveedor proveedor;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        loguearCon(EnumSet.allOf(PermisoCodigo.class));

        productoService = new ProductoServiceImpl(
                productoRepository, categoriaRepository, marcaRepository, proveedorRepository,
                productoMapper, productoValidator, sesionUsuario);

        lenient().when(productoValidator.validate(any())).thenReturn(new ValidationResult());

        categoria = Categoria.builder().nombre("Bebidas").build();
        categoria.setId(1L);
        marca = Marca.builder().nombre("Marca").build();
        marca.setId(2L);
        proveedor = Proveedor.builder().razonSocial("Proveedor SA").build();
        proveedor.setId(3L);

        lenient().when(categoriaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(categoria));
        lenient().when(marcaRepository.findByIdAndEstadoRegistro(2L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(marca));
        lenient().when(proveedorRepository.findByIdAndEstadoRegistro(3L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(proveedor));
    }

    private void loguearCon(EnumSet<PermisoCodigo> permisos) {
        sesionUsuario.iniciarSesion(new Usuario(), permisos);
    }

    private ProductoDto dto(Long id) {
        return ProductoDto.builder()
                .id(id)
                .codigo("  P001  ")
                .codigoBarras("   ")
                .nombre("  Gaseosa  ")
                .descripcion("  ")
                .precioCompra(new BigDecimal("100"))
                .precioVenta(new BigDecimal("150"))
                .stockMinimo(BigDecimal.valueOf(5))
                .categoriaId(1L)
                .marcaId(2L)
                .proveedorId(3L)
                .build();
    }

    private Producto producto(Long id, EstadoRegistro estado) {
        Producto producto = Producto.builder().nombre("Gaseosa").categoria(categoria).build();
        producto.setId(id);
        producto.setEstadoRegistro(estado);
        return producto;
    }

    // ------------------------------------------------------------------
    // Alta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("save() normaliza, carga las relaciones y guarda activo con stock 0")
    void saveNormalizaYGuarda() {
        ProductoDto dto = dto(null);
        Producto entidad = Producto.builder().stock(new BigDecimal("99")).build();
        entidad.setEstadoRegistro(EstadoRegistro.INACTIVO);

        when(productoMapper.toEntity(dto)).thenReturn(entidad);
        when(productoRepository.save(entidad)).thenReturn(entidad);

        productoService.save(dto);

        assertEquals("P001", dto.getCodigo());
        assertEquals("Gaseosa", dto.getNombre());
        assertNull(dto.getCodigoBarras());
        assertNull(dto.getDescripcion());
        assertTrue(dto.getManejaStock());
        assertFalse(dto.getPermiteStockNegativo());

        assertEquals(EstadoRegistro.ACTIVO, entidad.getEstadoRegistro());
        assertEquals(0, entidad.getStock().signum());
        assertSame(categoria, entidad.getCategoria());
        assertSame(marca, entidad.getMarca());
        assertSame(proveedor, entidad.getProveedor());
    }

    @Test
    @DisplayName("save() sin manejo de stock deja el stock mínimo en 0")
    void saveSinManejoDeStock() {
        ProductoDto dto = dto(null);
        dto.setManejaStock(false);
        dto.setPermiteStockNegativo(true);
        Producto entidad = new Producto();

        when(productoMapper.toEntity(dto)).thenReturn(entidad);
        when(productoRepository.save(entidad)).thenReturn(entidad);

        productoService.save(dto);

        assertEquals(BigDecimal.ZERO, dto.getStockMinimo());
        assertFalse(dto.getPermiteStockNegativo());
    }

    @Test
    @DisplayName("save() con errores de validación no guarda")
    void saveConErroresNoGuarda() {
        ValidationResult conError = new ValidationResult();
        conError.addError("codigo", "El código interno es obligatorio.");
        when(productoValidator.validate(any())).thenReturn(conError);

        assertThrows(ValidationException.class, () -> productoService.save(new ProductoDto()));

        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("save() con una categoría eliminada lanza EntityNotFoundException")
    void saveCategoriaEliminada() {
        ProductoDto dto = dto(null);
        when(productoMapper.toEntity(dto)).thenReturn(new Producto());
        when(categoriaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> productoService.save(dto));

        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("save() sin PRODUCTO_CREAR lanza AuthorizationException")
    void saveSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_VER));

        assertThrows(AuthorizationException.class, () -> productoService.save(dto(null)));

        verifyNoInteractions(productoRepository, productoValidator);
    }

    // ------------------------------------------------------------------
    // Edición
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update() actualiza el producto y conserva una marca dada de baja que ya tenía")
    void updateConservaMarcaActual() {
        Marca marcaDadaDeBaja = Marca.builder().nombre("Vieja").build();
        marcaDadaDeBaja.setId(7L);
        Producto existente = producto(1L, EstadoRegistro.ACTIVO);
        existente.setMarca(marcaDadaDeBaja);
        existente.setProveedor(proveedor);

        ProductoDto dto = dto(1L);
        dto.setMarcaId(7L);

        when(productoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(productoRepository.saveAndFlush(existente)).thenReturn(existente);

        productoService.update(dto);

        verify(productoMapper).updateEntity(dto, existente);
        assertSame(marcaDadaDeBaja, existente.getMarca());
        verify(marcaRepository, never()).findByIdAndEstadoRegistro(7L, EstadoRegistro.ACTIVO);
    }

    @Test
    @DisplayName("update() permite corregir un producto dado de baja sin habilitarlo")
    void updateDadoDeBaja() {
        Producto existente = producto(1L, EstadoRegistro.INACTIVO);

        when(productoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(productoRepository.saveAndFlush(existente)).thenReturn(existente);

        productoService.update(dto(1L));

        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        assertSame(categoria, existente.getCategoria());
    }

    @Test
    @DisplayName("update() no quita el permiso de stock negativo si el stock es negativo")
    void updateQuitaStockNegativoConStockNegativo() {
        Producto existente = Producto.builder().nombre("Gaseosa").categoria(categoria)
                .permiteStockNegativo(true).stock(new BigDecimal("-2")).build();
        existente.setId(1L);
        ProductoDto dto = dto(1L);
        dto.setPermiteStockNegativo(false);

        when(productoRepository.findById(1L)).thenReturn(Optional.of(existente));

        BusinessException ex = assertThrows(BusinessException.class, () -> productoService.update(dto));

        assertTrue(ex.getMessage().contains("stock negativo"));
        verify(productoMapper, never()).updateEntity(any(), any());
        verify(productoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update() no deja de controlar el stock si el stock es negativo")
    void updateDejaDeManejarStockConStockNegativo() {
        Producto existente = Producto.builder().nombre("Gaseosa").categoria(categoria)
                .permiteStockNegativo(true).stock(new BigDecimal("-2")).build();
        existente.setId(1L);
        ProductoDto dto = dto(1L);
        dto.setManejaStock(false);
        dto.setPermiteStockNegativo(true);

        when(productoRepository.findById(1L)).thenReturn(Optional.of(existente));

        BusinessException ex = assertThrows(BusinessException.class, () -> productoService.update(dto));

        assertTrue(ex.getMessage().contains("dejar de controlar el stock"));
        verify(productoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update() con una versión vieja lanza ConcurrencyException y no guarda")
    void updateConVersionVieja() {
        Producto existente = Producto.builder().nombre("Gaseosa").categoria(categoria).version(3L).build();
        existente.setId(1L);
        ProductoDto dto = dto(1L);
        dto.setVersion(2L);

        when(productoRepository.findById(1L)).thenReturn(Optional.of(existente));

        assertThrows(ConcurrencyException.class, () -> productoService.update(dto));

        verify(productoMapper, never()).updateEntity(any(), any());
        verify(productoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update() traduce un conflicto de @Version al guardar en ConcurrencyException")
    void updateConflictoAlGuardar() {
        Producto existente = producto(1L, EstadoRegistro.ACTIVO);

        when(productoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(productoRepository.saveAndFlush(existente))
                .thenThrow(new ObjectOptimisticLockingFailureException(Producto.class, 1L));

        assertThrows(ConcurrencyException.class, () -> productoService.update(dto(1L)));
    }

    @Test
    @DisplayName("update() no permite pasar a una unidad entera si el stock tiene decimales")
    void updateUnidadEnteraConStockFraccionario() {
        Producto existente = Producto.builder().nombre("Queso").categoria(categoria)
                .unidadMedida(UnidadMedida.KILOGRAMO).stock(new BigDecimal("1.5")).build();
        existente.setId(1L);
        ProductoDto dto = dto(1L);
        dto.setUnidadMedida(UnidadMedida.UNIDAD);

        when(productoRepository.findById(1L)).thenReturn(Optional.of(existente));

        BusinessException ex = assertThrows(BusinessException.class, () -> productoService.update(dto));

        assertTrue(ex.getMessage().contains("tiene decimales"));
        verify(productoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update() de un producto inexistente lanza EntityNotFoundException")
    void updateInexistente() {
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> productoService.update(dto(1L)));

        verify(productoRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("update() sin PRODUCTO_EDITAR lanza AuthorizationException")
    void updateSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_VER));

        assertThrows(AuthorizationException.class, () -> productoService.update(dto(1L)));

        verifyNoInteractions(productoRepository);
    }

    // ------------------------------------------------------------------
    // Baja y habilitación
    // ------------------------------------------------------------------

    @Test
    @DisplayName("darDeBaja() marca el producto como inactivo sin borrarlo")
    void darDeBaja() {
        Producto existente = producto(1L, EstadoRegistro.ACTIVO);
        when(productoRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));

        productoService.darDeBaja(1L);

        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(productoRepository).save(existente);
        verify(productoRepository, never()).delete(any());
        verify(productoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("delete() no borra: da de baja")
    void deleteDaDeBaja() {
        Producto existente = producto(1L, EstadoRegistro.ACTIVO);
        when(productoRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(existente));

        productoService.delete(1L);

        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(productoRepository, never()).delete(any());
        verify(productoRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("darDeBaja() de un producto ya dado de baja lanza EntityNotFoundException")
    void darDeBajaYaDadoDeBaja() {
        when(productoRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> productoService.darDeBaja(1L));
    }

    @Test
    @DisplayName("darDeBaja() sin PRODUCTO_DAR_BAJA lanza AuthorizationException")
    void darDeBajaSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_VER, PermisoCodigo.PRODUCTO_EDITAR));

        assertThrows(AuthorizationException.class, () -> productoService.darDeBaja(1L));

        verifyNoInteractions(productoRepository);
    }

    @Test
    @DisplayName("habilitar() vuelve a activar un producto dado de baja")
    void habilitar() {
        Producto existente = producto(1L, EstadoRegistro.INACTIVO);
        when(productoRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.INACTIVO))
                .thenReturn(Optional.of(existente));

        productoService.habilitar(1L);

        assertEquals(EstadoRegistro.ACTIVO, existente.getEstadoRegistro());
        verify(productoRepository).save(existente);
    }

    @Test
    @DisplayName("habilitar() falla si la categoría del producto fue eliminada")
    void habilitarConCategoriaEliminada() {
        categoria.setEstadoRegistro(EstadoRegistro.INACTIVO);
        Producto existente = producto(1L, EstadoRegistro.INACTIVO);
        when(productoRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.INACTIVO))
                .thenReturn(Optional.of(existente));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productoService.habilitar(1L));

        assertTrue(ex.getMessage().contains("categoría"));
        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("habilitar() falla si otro producto activo tomó su código interno")
    void habilitarConCodigoOcupado() {
        Producto existente = producto(1L, EstadoRegistro.INACTIVO);
        existente.setCodigo("P001");
        Producto otro = producto(2L, EstadoRegistro.ACTIVO);
        otro.setNombre("Agua");
        when(productoRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.INACTIVO))
                .thenReturn(Optional.of(existente));
        when(productoRepository.findFirstByCodigoIgnoreCaseAndEstadoRegistroAndIdNot(
                "P001", EstadoRegistro.ACTIVO, 1L))
                .thenReturn(Optional.of(otro));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productoService.habilitar(1L));

        assertTrue(ex.getMessage().contains("Agua"));
        assertEquals(EstadoRegistro.INACTIVO, existente.getEstadoRegistro());
        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("habilitar() falla si otro producto activo tomó su código de barras")
    void habilitarConCodigoBarrasOcupado() {
        Producto existente = producto(1L, EstadoRegistro.INACTIVO);
        existente.setCodigo("P001");
        existente.setCodigoBarras("7790001000011");
        Producto otro = producto(2L, EstadoRegistro.ACTIVO);
        otro.setNombre("Agua");
        when(productoRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.INACTIVO))
                .thenReturn(Optional.of(existente));
        when(productoRepository.findFirstByCodigoBarrasAndEstadoRegistroAndIdNot(
                "7790001000011", EstadoRegistro.ACTIVO, 1L))
                .thenReturn(Optional.of(otro));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> productoService.habilitar(1L));

        assertTrue(ex.getMessage().contains("código de barras"));
        verify(productoRepository, never()).save(any());
    }

    @Test
    @DisplayName("habilitar() sin PRODUCTO_HABILITAR lanza AuthorizationException")
    void habilitarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_VER, PermisoCodigo.PRODUCTO_DAR_BAJA));

        assertThrows(AuthorizationException.class, () -> productoService.habilitar(1L));

        verifyNoInteractions(productoRepository);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findAll() y search() listan solo los activos")
    void findAllYSearchActivos() {
        when(productoRepository.buscar(any(), any())).thenReturn(List.of());

        productoService.findAll();
        productoService.search("  coca ");

        verify(productoRepository).buscar("", EstadoRegistro.ACTIVO);
        verify(productoRepository).buscar("coca", EstadoRegistro.ACTIVO);
    }

    @Test
    @DisplayName("buscar() respeta el filtro de estado")
    void buscarPorEstado() {
        when(productoRepository.buscar(any(), any())).thenReturn(List.of());

        productoService.buscar("", FiltroEstado.DADOS_DE_BAJA);
        productoService.buscar(null, FiltroEstado.TODOS);

        verify(productoRepository).buscar("", EstadoRegistro.INACTIVO);
        verify(productoRepository).buscar("", null);
    }

    @Test
    @DisplayName("buscar() sin PRODUCTO_VER lanza AuthorizationException")
    void buscarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_CREAR));

        assertThrows(AuthorizationException.class,
                () -> productoService.buscar("", FiltroEstado.ACTIVOS));
    }

    @Test
    @DisplayName("findById() devuelve también productos dados de baja")
    void findByIdDadoDeBaja() {
        Producto existente = producto(1L, EstadoRegistro.INACTIVO);
        ProductoDto mapeado = ProductoDto.builder().id(1L).estadoRegistro(EstadoRegistro.INACTIVO).build();
        when(productoRepository.findById(1L)).thenReturn(Optional.of(existente));
        when(productoMapper.toDto(existente)).thenReturn(mapeado);

        Optional<ProductoDto> resultado = productoService.findById(1L);

        assertTrue(resultado.isPresent());
        assertFalse(resultado.get().isActivo());
    }

    @Test
    @DisplayName("count() cuenta solo los activos")
    void countActivos() {
        when(productoRepository.countByEstadoRegistro(EstadoRegistro.ACTIVO)).thenReturn(4L);

        assertEquals(4L, productoService.count());
    }
}
