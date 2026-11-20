package org.gestionComercio.validation;

import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.dto.compra.DetalleCompraDto;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.enums.EstadoCompra;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.repository.CompraRepository;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompraValidatorTest {

    private static final Long PROVEEDOR = 10L;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private ProveedorRepository proveedorRepository;

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private CompraValidator validator;

    private final Map<Long, Producto> productos = new HashMap<>();

    @BeforeEach
    void setUp() {
        productos.put(1L, producto(1L, "Gaseosa", UnidadMedida.UNIDAD, EstadoRegistro.ACTIVO));
        productos.put(2L, producto(2L, "Queso", UnidadMedida.KILOGRAMO, EstadoRegistro.ACTIVO));
        productos.put(3L, producto(3L, "Discontinuado", UnidadMedida.UNIDAD, EstadoRegistro.INACTIVO));

        lenient().when(productoRepository.findAllById(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().map(productos::get).filter(Objects::nonNull).toList();
        });
        lenient().when(proveedorRepository.findByIdAndEstadoRegistro(anyLong(), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.empty());
        lenient().when(proveedorRepository.findByIdAndEstadoRegistro(PROVEEDOR, EstadoRegistro.ACTIVO))
                .thenReturn(Optional.of(Proveedor.builder().razonSocial("Distribuidora").build()));
    }

    private static Producto producto(Long id, String nombre, UnidadMedida unidad, EstadoRegistro estado) {
        Producto producto = Producto.builder().nombre(nombre).unidadMedida(unidad).build();
        producto.setId(id);
        producto.setEstadoRegistro(estado);
        return producto;
    }

    private static DetalleCompraDto renglon(Long productoId, String cantidad, String precio) {
        return DetalleCompraDto.builder()
                .productoId(productoId)
                .cantidad(cantidad == null ? null : new BigDecimal(cantidad))
                .precioCompra(precio == null ? null : new BigDecimal(precio))
                .build();
    }

    private static CompraDto compra(DetalleCompraDto... renglones) {
        return CompraDto.builder()
                .proveedorId(PROVEEDOR)
                .formaPago(FormaPago.TRANSFERENCIA)
                .estadoCompra(EstadoCompra.RECIBIDA)
                .fecha(LocalDateTime.now().minusMinutes(1))
                .detalles(new ArrayList<>(List.of(renglones)))
                .build();
    }

    private static List<String> campos(ValidationResult result) {
        return result.getErrors().stream().map(ValidationError::getField).toList();
    }

    @Test
    @DisplayName("Una compra con proveedor y renglones válidos no tiene errores")
    void compraValida() {
        CompraDto dto = compra(renglon(1L, "24", "900.00"), renglon(2L, "3.5", "6000"));
        dto.setNumeroFactura("A-0001-00001234");

        assertThat(validator.validate(dto).isValid()).isTrue();
    }

    @Test
    @DisplayName("Una compra pendiente es válida")
    void pendienteValida() {
        CompraDto dto = compra(renglon(1L, "1", "100"));
        dto.setEstadoCompra(EstadoCompra.PENDIENTE);

        assertThat(validator.validate(dto).isValid()).isTrue();
    }

    @Test
    @DisplayName("El precio de compra puede ser cero (bonificación)")
    void precioCero() {
        assertThat(validator.validate(compra(renglon(1L, "1", "0"))).isValid()).isTrue();
    }

    @Test
    @DisplayName("Sin proveedor, forma de pago ni renglones informa los tres errores")
    void obligatorios() {
        CompraDto dto = compra();
        dto.setProveedorId(null);
        dto.setFormaPago(null);

        assertThat(campos(validator.validate(dto)))
                .containsExactlyInAnyOrder("proveedorId", "formaPago", "detalles");
    }

    @Test
    @DisplayName("Un proveedor dado de baja o inexistente no es válido")
    void proveedorInactivo() {
        CompraDto dto = compra(renglon(1L, "1", "100"));
        dto.setProveedorId(99L);

        assertThat(campos(validator.validate(dto))).containsExactly("proveedorId");
    }

    @Test
    @DisplayName("Una factura ya registrada para el proveedor se rechaza")
    void facturaDuplicada() {
        when(compraRepository.existeFactura(PROVEEDOR, "A-1")).thenReturn(true);
        CompraDto dto = compra(renglon(1L, "1", "100"));
        dto.setNumeroFactura("A-1");

        ValidationResult result = validator.validate(dto);

        assertThat(campos(result)).containsExactly("numeroFactura");
        assertThat(result.getErrors().get(0).getMessage()).contains("A-1");
    }

    @Test
    @DisplayName("Sin número de factura no consulta duplicados")
    void sinFacturaNoConsulta() {
        validator.validate(compra(renglon(1L, "1", "100")));

        verify(compraRepository, never()).existeFactura(any(), any());
    }

    @Test
    @DisplayName("Un número de factura demasiado largo se rechaza")
    void facturaLarga() {
        CompraDto dto = compra(renglon(1L, "1", "100"));
        dto.setNumeroFactura("X".repeat(CompraValidator.NUMERO_FACTURA_MAX + 1));

        assertThat(campos(validator.validate(dto))).containsExactly("numeroFactura");
    }

    @Test
    @DisplayName("Una compra nueva no puede quedar anulada ni cancelada")
    void estadoInvalido() {
        CompraDto dto = compra(renglon(1L, "1", "100"));
        dto.setEstadoCompra(EstadoCompra.ANULADA);

        assertThat(campos(validator.validate(dto))).containsExactly("estadoCompra");
    }

    @Test
    @DisplayName("La fecha no puede ser futura")
    void fechaFutura() {
        CompraDto dto = compra(renglon(1L, "1", "100"));
        dto.setFecha(LocalDateTime.now().plusDays(1));

        assertThat(campos(validator.validate(dto))).containsExactly("fecha");
    }

    @Test
    @DisplayName("Valida producto, cantidad y precio de cada renglón")
    void renglonesInvalidos() {
        CompraDto dto = compra(
                renglon(null, "1", "100"),
                renglon(3L, "1", "100"),
                renglon(1L, "1.5", "100"),
                renglon(2L, "0", "-1"),
                renglon(1L, "1", "10.555"),
                renglon(1L, "1", null));

        assertThat(campos(validator.validate(dto))).containsExactly(
                "detalles[0].productoId",
                "detalles[1].productoId",
                "detalles[2].cantidad",
                "detalles[3].cantidad",
                "detalles[3].precioCompra",
                "detalles[4].precioCompra",
                "detalles[5].precioCompra");
    }

    @Test
    @DisplayName("Un producto por unidad no admite decimales y lo dice con 'se compra'")
    void unidadNoFraccionable() {
        ValidationResult result = validator.validate(compra(renglon(1L, "1.5", "100")));

        assertThat(result.getErrors().get(0).getMessage()).contains("se compra por");
    }

    @Test
    @DisplayName("Un total mayor al máximo se rechaza")
    void totalExcesivo() {
        CompraDto dto = compra(renglon(1L, "2", "9999999999.99"));

        assertThat(campos(validator.validate(dto))).containsExactly("detalles");
    }

}
