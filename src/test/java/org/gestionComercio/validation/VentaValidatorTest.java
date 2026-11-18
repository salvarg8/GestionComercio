package org.gestionComercio.validation;

import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.repository.ClienteRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaValidatorTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private VentaValidator validator;

    private final Map<Long, Producto> productos = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        productos.put(1L, producto(1L, "Gaseosa", "1500.00", UnidadMedida.UNIDAD, EstadoRegistro.ACTIVO));
        productos.put(2L, producto(2L, "Queso", "8000.00", UnidadMedida.KILOGRAMO, EstadoRegistro.ACTIVO));
        productos.put(3L, producto(3L, "Discontinuado", "100.00", UnidadMedida.UNIDAD, EstadoRegistro.INACTIVO));

        lenient().when(productoRepository.findAllById(any())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().map(productos::get).filter(java.util.Objects::nonNull).toList();
        });
    }

    private static Producto producto(Long id, String nombre, String precio, UnidadMedida unidad, EstadoRegistro estado) {
        Producto producto = Producto.builder()
                .nombre(nombre).precioVenta(new BigDecimal(precio)).unidadMedida(unidad).build();
        producto.setId(id);
        producto.setEstadoRegistro(estado);
        return producto;
    }

    private static DetalleVentaDto renglon(Long productoId, String cantidad) {
        return DetalleVentaDto.builder()
                .productoId(productoId)
                .cantidad(cantidad == null ? null : new BigDecimal(cantidad))
                .build();
    }

    private static VentaDto venta(DetalleVentaDto... renglones) {
        return VentaDto.builder()
                .formaPago(FormaPago.EFECTIVO)
                .descuento(BigDecimal.ZERO)
                .detalles(new ArrayList<>(List.of(renglones)))
                .build();
    }

    private static List<String> campos(ValidationResult result) {
        return result.getErrors().stream().map(ValidationError::getField).toList();
    }

    @Test
    @DisplayName("Una venta a consumidor final con renglones válidos no tiene errores")
    void ventaValida() {
        ValidationResult result = validator.validate(venta(renglon(1L, "2"), renglon(2L, "0.750")));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Sin renglones informa que la venta debe tener al menos un producto")
    void sinRenglones() {
        ValidationResult result = validator.validate(venta());

        assertThat(campos(result)).containsExactly("detalles");
    }

    @Test
    @DisplayName("Cantidad cero o negativa es inválida")
    void cantidadNoPositiva() {
        ValidationResult result = validator.validate(venta(renglon(1L, "0"), renglon(1L, "-1")));

        assertThat(campos(result)).containsExactly("detalles[0].cantidad", "detalles[1].cantidad");
        assertThat(result.getErrors().get(1).getMessage()).startsWith("Renglón 2:");
    }

    @Test
    @DisplayName("Renglón sin producto, sin cantidad o con producto dado de baja: informa todos juntos")
    void erroresDeRenglonesJuntos() {
        ValidationResult result = validator.validate(venta(
                renglon(null, "1"), renglon(1L, null), renglon(3L, "1"), renglon(99L, "1")));

        assertThat(campos(result)).containsExactly(
                "detalles[0].productoId", "detalles[1].cantidad",
                "detalles[2].productoId", "detalles[3].productoId");
    }

    @Test
    @DisplayName("Un producto por unidad no admite cantidades con decimales")
    void unidadNoFraccionable() {
        ValidationResult result = validator.validate(venta(renglon(1L, "1.5")));

        assertThat(campos(result)).containsExactly("detalles[0].cantidad");
    }

    @Test
    @DisplayName("Más de tres decimales en la cantidad es inválido")
    void cantidadConDemasiadosDecimales() {
        ValidationResult result = validator.validate(venta(renglon(2L, "0.1234")));

        assertThat(campos(result)).containsExactly("detalles[0].cantidad");
    }

    @Test
    @DisplayName("La forma de pago es obligatoria")
    void formaPagoObligatoria() {
        VentaDto dto = venta(renglon(1L, "1"));
        dto.setFormaPago(null);

        assertThat(campos(validator.validate(dto))).containsExactly("formaPago");
    }

    @Test
    @DisplayName("Cuenta corriente exige un cliente")
    void cuentaCorrienteSinCliente() {
        VentaDto dto = venta(renglon(1L, "1"));
        dto.setFormaPago(FormaPago.CUENTA_CORRIENTE);

        assertThat(campos(validator.validate(dto))).containsExactly("clienteId");
    }

    @Test
    @DisplayName("Un cliente dado de baja o inexistente no se puede elegir")
    void clienteInactivo() {
        when(clienteRepository.findByIdAndEstadoRegistro(eq(5L), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.empty());
        VentaDto dto = venta(renglon(1L, "1"));
        dto.setClienteId(5L);

        assertThat(campos(validator.validate(dto))).containsExactly("clienteId");
    }

    @Test
    @DisplayName("Un cliente activo es válido, también en cuenta corriente")
    void clienteActivo() {
        when(clienteRepository.findByIdAndEstadoRegistro(eq(5L), eq(EstadoRegistro.ACTIVO)))
                .thenReturn(Optional.of(new Cliente()));
        VentaDto dto = venta(renglon(1L, "1"));
        dto.setClienteId(5L);
        dto.setFormaPago(FormaPago.CUENTA_CORRIENTE);

        assertThat(validator.validate(dto).isValid()).isTrue();
    }

    @Test
    @DisplayName("El descuento no puede ser negativo ni tener más de dos decimales")
    void descuentoInvalido() {
        VentaDto negativo = venta(renglon(1L, "1"));
        negativo.setDescuento(new BigDecimal("-1"));
        VentaDto decimales = venta(renglon(1L, "1"));
        decimales.setDescuento(new BigDecimal("1.001"));

        assertThat(campos(validator.validate(negativo))).containsExactly("descuento");
        assertThat(campos(validator.validate(decimales))).containsExactly("descuento");
    }

    @Test
    @DisplayName("El descuento puede ser igual al subtotal pero no mayor")
    void descuentoContraSubtotal() {
        // 2 × 1500 = 3000
        VentaDto igual = venta(renglon(1L, "2"));
        igual.setDescuento(new BigDecimal("3000"));
        VentaDto mayor = venta(renglon(1L, "2"));
        mayor.setDescuento(new BigDecimal("3000.01"));

        assertThat(validator.validate(igual).isValid()).isTrue();
        assertThat(campos(validator.validate(mayor))).containsExactly("descuento");
    }

    @Test
    @DisplayName("Un subtotal que no entra en la columna de importes es inválido")
    void subtotalExcedido() {
        ValidationResult result = validator.validate(venta(renglon(1L, "9999999")));

        assertThat(campos(result)).containsExactly("detalles");
    }

}
