package org.gestionComercio.validation;

import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.enums.AlicuotaIVA;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.repository.CategoriaRepository;
import org.gestionComercio.repository.MarcaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.ProveedorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoValidatorTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private MarcaRepository marcaRepository;

    @Mock
    private ProveedorRepository proveedorRepository;

    @InjectMocks
    private ProductoValidator validator;

    @BeforeEach
    void setUp() {
        // Por defecto: códigos libres y relaciones vigentes.
        lenient().when(productoRepository.findFirstByCodigoIgnoreCaseAndEstadoRegistro(anyString(), any())).thenReturn(Optional.empty());
        lenient().when(productoRepository.findFirstByCodigoIgnoreCaseAndEstadoRegistroAndIdNot(anyString(), any(), anyLong())).thenReturn(Optional.empty());
        lenient().when(productoRepository.findFirstByCodigoBarrasAndEstadoRegistro(anyString(), any())).thenReturn(Optional.empty());
        lenient().when(productoRepository.findFirstByCodigoBarrasAndEstadoRegistroAndIdNot(anyString(), any(), anyLong())).thenReturn(Optional.empty());

        lenient().when(categoriaRepository.findByIdAndEstadoRegistro(any(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.of(new Categoria()));
        lenient().when(marcaRepository.findByIdAndEstadoRegistro(any(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.of(new Marca()));
        lenient().when(proveedorRepository.findByIdAndEstadoRegistro(any(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.of(new Proveedor()));
    }

    private ProductoDto valido() {
        return ProductoDto.builder()
                .codigo("P001")
                .codigoBarras("7790001000011")
                .nombre("Gaseosa cola 1,5 L")
                .precioCompra(new BigDecimal("1000.00"))
                .precioVenta(new BigDecimal("1500.00"))
                .stockMinimo(BigDecimal.valueOf(5))
                .manejaStock(true)
                .permiteStockNegativo(false)
                .unidadMedida(UnidadMedida.UNIDAD)
                .alicuotaIVA(AlicuotaIVA.IVA_21)
                .categoriaId(1L)
                .marcaId(2L)
                .proveedorId(3L)
                .build();
    }

    private boolean tieneError(ValidationResult result, String campo, String texto) {
        return result.getErrors().stream()
                .anyMatch(e -> e.getField().equals(campo) && e.getMessage().contains(texto));
    }

    private Producto producto(Long id, String nombre, EstadoRegistro estado) {
        Producto producto = Producto.builder().nombre(nombre).build();
        producto.setId(id);
        producto.setEstadoRegistro(estado);
        return producto;
    }

    @Test
    @DisplayName("un producto válido no tiene errores")
    void valido_sinErrores() {
        assertFalse(validator.validate(valido()).hasErrors());
    }

    // ------------------------------------------------------------------
    // Campos obligatorios
    // ------------------------------------------------------------------

    @Test
    @DisplayName("informa todos los campos obligatorios faltantes")
    void obligatorios() {
        ProductoDto dto = ProductoDto.builder().manejaStock(true).build();

        ValidationResult result = validator.validate(dto);

        assertTrue(tieneError(result, "codigo", "obligatorio"));
        assertTrue(tieneError(result, "nombre", "obligatorio"));
        assertTrue(tieneError(result, "precioCompra", "obligatorio"));
        assertTrue(tieneError(result, "precioVenta", "obligatorio"));
        assertTrue(tieneError(result, "stockMinimo", "obligatorio"));
        assertTrue(tieneError(result, "unidadMedida", "unidad de medida"));
        assertTrue(tieneError(result, "alicuotaIVA", "IVA"));
        assertTrue(tieneError(result, "categoriaId", "categoría"));
        assertTrue(tieneError(result, "marcaId", "marca"));
        assertTrue(tieneError(result, "proveedorId", "proveedor"));
    }

    @Test
    @DisplayName("el código de barras y la descripción son opcionales")
    void opcionales() {
        ProductoDto dto = valido();
        dto.setCodigoBarras(null);
        dto.setDescripcion(null);

        assertFalse(validator.validate(dto).hasErrors());
    }

    @Test
    @DisplayName("sin manejo de stock no exige stock mínimo")
    void sinManejoDeStock() {
        ProductoDto dto = valido();
        dto.setManejaStock(false);
        dto.setStockMinimo(null);

        assertFalse(validator.validate(dto).hasErrors());
    }

    @Test
    @DisplayName("controla los largos máximos")
    void largos() {
        ProductoDto dto = valido();
        dto.setCodigo("x".repeat(ProductoValidator.CODIGO_MAX + 1));
        dto.setCodigoBarras("1".repeat(ProductoValidator.CODIGO_BARRAS_MAX + 1));
        dto.setNombre("x".repeat(ProductoValidator.NOMBRE_MAX + 1));
        dto.setDescripcion("x".repeat(ProductoValidator.DESCRIPCION_MAX + 1));

        ValidationResult result = validator.validate(dto);

        assertTrue(tieneError(result, "codigo", "superar"));
        assertTrue(tieneError(result, "codigoBarras", "superar"));
        assertTrue(tieneError(result, "nombre", "superar"));
        assertTrue(tieneError(result, "descripcion", "superar"));
    }

    @Test
    @DisplayName("el código de barras no puede tener espacios")
    void codigoBarrasConEspacios() {
        ProductoDto dto = valido();
        dto.setCodigoBarras("779 0001");

        assertTrue(tieneError(validator.validate(dto), "codigoBarras", "espacios"));
    }

    // ------------------------------------------------------------------
    // Códigos duplicados
    // ------------------------------------------------------------------

    @Test
    @DisplayName("no permite repetir el código interno de un producto activo")
    void codigoDuplicado() {
        when(productoRepository.findFirstByCodigoIgnoreCaseAndEstadoRegistro("P001", EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(producto(9L, "Otro", EstadoRegistro.ACTIVO)));

        assertTrue(tieneError(validator.validate(valido()), "codigo", "Ya existe un producto con ese código"));
    }

    @Test
    @DisplayName("un producto dado de baja no ocupa su código: sólo se buscan duplicados entre los activos")
    void codigoDeProductoDadoDeBaja() {
        assertFalse(validator.validate(valido()).hasErrors());

        verify(productoRepository).findFirstByCodigoIgnoreCaseAndEstadoRegistro("P001", EstadoRegistro.ACTIVO);
        verify(productoRepository).findFirstByCodigoBarrasAndEstadoRegistro("7790001000011", EstadoRegistro.ACTIVO);
    }

    @Test
    @DisplayName("al editar, el código interno se compara contra los demás productos")
    void codigoAlEditar() {
        ProductoDto dto = valido();
        dto.setId(1L);
        when(productoRepository.findFirstByCodigoIgnoreCaseAndEstadoRegistroAndIdNot("P001", EstadoRegistro.ACTIVO, 1L))
                .thenReturn(Optional.of(producto(9L, "Otro", EstadoRegistro.ACTIVO)));

        assertTrue(tieneError(validator.validate(dto), "codigo", "Ya existe"));
    }

    @Test
    @DisplayName("no permite repetir el código de barras")
    void codigoBarrasDuplicado() {
        when(productoRepository.findFirstByCodigoBarrasAndEstadoRegistro("7790001000011", EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(producto(9L, "Agua", EstadoRegistro.ACTIVO)));

        assertTrue(tieneError(validator.validate(valido()), "codigoBarras", "Agua"));
    }

    @Test
    @DisplayName("al editar, el código de barras se compara contra los demás productos")
    void codigoBarrasAlEditar() {
        ProductoDto dto = valido();
        dto.setId(1L);

        assertFalse(validator.validate(dto).hasErrors());
    }

    // ------------------------------------------------------------------
    // Precios y stock
    // ------------------------------------------------------------------

    @Test
    @DisplayName("los precios no pueden ser negativos")
    void preciosNegativos() {
        ProductoDto dto = valido();
        dto.setPrecioCompra(new BigDecimal("-1"));
        dto.setPrecioVenta(new BigDecimal("-0.01"));

        ValidationResult result = validator.validate(dto);

        assertTrue(tieneError(result, "precioCompra", "negativo"));
        assertTrue(tieneError(result, "precioVenta", "negativo"));
    }

    @Test
    @DisplayName("acepta precios en cero")
    void preciosEnCero() {
        ProductoDto dto = valido();
        dto.setPrecioCompra(BigDecimal.ZERO);
        dto.setPrecioVenta(BigDecimal.ZERO);

        assertFalse(validator.validate(dto).hasErrors());
    }

    @Test
    @DisplayName("el precio de venta no puede ser menor al de compra")
    void ventaMenorACompra() {
        ProductoDto dto = valido();
        dto.setPrecioVenta(new BigDecimal("999.99"));

        assertTrue(tieneError(validator.validate(dto), "precioVenta", "menor"));
    }

    @Test
    @DisplayName("los precios admiten hasta dos decimales")
    void preciosDecimales() {
        ProductoDto dto = valido();
        dto.setPrecioCompra(new BigDecimal("10.555"));
        dto.setPrecioVenta(new BigDecimal("20.500"));

        ValidationResult result = validator.validate(dto);

        assertTrue(tieneError(result, "precioCompra", "decimales"));
        assertFalse(tieneError(result, "precioVenta", "decimales"));
    }

    @Test
    @DisplayName("los precios no pueden superar el tamaño de la columna")
    void precioDemasiadoGrande() {
        ProductoDto dto = valido();
        dto.setPrecioVenta(new BigDecimal("12345678901"));

        assertTrue(tieneError(validator.validate(dto), "precioVenta", "demasiado grande"));
    }

    @Test
    @DisplayName("el stock mínimo no puede ser negativo")
    void stockMinimoNegativo() {
        ProductoDto dto = valido();
        dto.setStockMinimo(BigDecimal.valueOf(-1));

        assertTrue(tieneError(validator.validate(dto), "stockMinimo", "negativo"));
    }

    @Test
    @DisplayName("el stock mínimo admite decimales sólo si la unidad es fraccionable")
    void stockMinimoConDecimales() {
        ProductoDto dto = valido();
        dto.setStockMinimo(new BigDecimal("2.5"));

        dto.setUnidadMedida(UnidadMedida.UNIDAD);
        assertTrue(tieneError(validator.validate(dto), "stockMinimo", "no admite decimales"));

        dto.setUnidadMedida(UnidadMedida.KILOGRAMO);
        assertFalse(validator.validate(dto).hasErrors());

        dto.setStockMinimo(new BigDecimal("2.5001"));
        assertTrue(tieneError(validator.validate(dto), "stockMinimo", "3 decimales"));
    }

    // ------------------------------------------------------------------
    // Relaciones
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la categoría tiene que estar vigente")
    void categoriaEliminada() {
        when(categoriaRepository.findByIdAndEstadoRegistro(1L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        assertTrue(tieneError(validator.validate(valido()), "categoriaId", "eliminada"));
    }

    @Test
    @DisplayName("un producto nuevo no puede usar una marca o un proveedor dados de baja")
    void marcaYProveedorDadosDeBaja() {
        when(marcaRepository.findByIdAndEstadoRegistro(2L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());
        when(proveedorRepository.findByIdAndEstadoRegistro(3L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        ValidationResult result = validator.validate(valido());

        assertTrue(tieneError(result, "marcaId", "dada de baja"));
        assertTrue(tieneError(result, "proveedorId", "dado de baja"));
    }

    @Test
    @DisplayName("al editar, conserva la marca y el proveedor dados de baja que ya tenía")
    void conservaMarcaYProveedorActuales() {
        Marca marca = new Marca();
        marca.setId(2L);
        Proveedor proveedor = new Proveedor();
        proveedor.setId(3L);
        Producto actual = producto(1L, "Gaseosa", EstadoRegistro.ACTIVO);
        actual.setMarca(marca);
        actual.setProveedor(proveedor);

        when(productoRepository.findById(1L)).thenReturn(Optional.of(actual));
        lenient().when(marcaRepository.findByIdAndEstadoRegistro(2L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());
        lenient().when(proveedorRepository.findByIdAndEstadoRegistro(3L, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.empty());

        ProductoDto dto = valido();
        dto.setId(1L);

        assertFalse(validator.validate(dto).hasErrors());
    }
}
