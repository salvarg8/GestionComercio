package org.gestionComercio.controller.compra;

import org.gestionComercio.controller.compra.RenglonesCompra.Renglon;
import org.gestionComercio.dto.compra.DetalleCompraDto;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.enums.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenglonesCompraTest {

    private final RenglonesCompra renglones = new RenglonesCompra();

    private static ProductoDto producto(Long id, String precioCompra, UnidadMedida unidad) {
        return ProductoDto.builder()
                .id(id).codigo("P" + id).nombre("Producto " + id)
                .precioCompra(precioCompra == null ? null : new BigDecimal(precioCompra))
                .unidadMedida(unidad)
                .build();
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("Agregar el mismo producto con el mismo costo suma la cantidad a su renglón")
    void mismoProductoMismoCosto() {
        ProductoDto gaseosa = producto(1L, "900", UnidadMedida.UNIDAD);

        Renglon primero = renglones.agregar(gaseosa, bd("12"), bd("900"));
        Renglon segundo = renglones.agregar(gaseosa, bd("12"), bd("900.00"));

        assertThat(segundo).isSameAs(primero);
        assertThat(renglones.getRenglones()).hasSize(1);
        assertThat(primero.getCantidad()).isEqualByComparingTo("24");
    }

    @Test
    @DisplayName("El mismo producto con otro costo va en un renglón aparte")
    void mismoProductoOtroCosto() {
        ProductoDto gaseosa = producto(1L, "900", UnidadMedida.UNIDAD);

        renglones.agregar(gaseosa, bd("12"), bd("900"));
        renglones.agregar(gaseosa, bd("1"), bd("0"));

        assertThat(renglones.getRenglones()).hasSize(2);
    }

    @Test
    @DisplayName("El total suma los subtotales redondeados como el servicio")
    void total() {
        renglones.agregar(producto(1L, "900", UnidadMedida.UNIDAD), bd("24"), bd("900"));
        renglones.agregar(producto(2L, "6000", UnidadMedida.KILOGRAMO), bd("0.333"), bd("1000.15"));

        // 24 × 900 = 21600; 0,333 × 1000,15 = 333,04995 → 333,05
        assertThat(renglones.total()).isEqualByComparingTo("21933.05");
    }

    @Test
    @DisplayName("Rechaza cantidades inválidas, decimales en productos por unidad y costos inválidos")
    void validaciones() {
        ProductoDto gaseosa = producto(1L, "900", UnidadMedida.UNIDAD);

        assertThatThrownBy(() -> renglones.agregar(gaseosa, bd("0"), bd("900")))
                .hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> renglones.agregar(gaseosa, bd("1.5"), bd("900")))
                .hasMessageContaining("se compra por");
        assertThatThrownBy(() -> renglones.agregar(gaseosa, bd("1"), bd("-1")))
                .hasMessageContaining("negativo");
        assertThatThrownBy(() -> renglones.agregar(gaseosa, bd("1"), bd("10.555")))
                .hasMessageContaining("dos decimales");
        assertThatThrownBy(() -> renglones.agregar(gaseosa, bd("1"), null))
                .hasMessageContaining("costo");

        assertThat(renglones.estaVacio()).isTrue();
    }

    @Test
    @DisplayName("Cambiar cantidad o costo valida y no modifica el renglón si es inválido")
    void cambiarCantidadYCosto() {
        Renglon renglon = renglones.agregar(producto(1L, "900", UnidadMedida.UNIDAD), bd("2"), bd("900"));

        renglones.cambiarCantidad(renglon, bd("5"));
        renglones.cambiarPrecio(renglon, bd("950.50"));

        assertThat(renglon.getSubtotal()).isEqualByComparingTo("4752.50");

        assertThatThrownBy(() -> renglones.cambiarCantidad(renglon, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> renglones.cambiarPrecio(renglon, bd("-5"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(renglon.getCantidad()).isEqualByComparingTo("5");
        assertThat(renglon.getPrecio()).isEqualByComparingTo("950.50");
    }

    @Test
    @DisplayName("Detecta si algún costo difiere del precio de compra actual del producto")
    void cambiosDePrecio() {
        Renglon renglon = renglones.agregar(producto(1L, "900", UnidadMedida.UNIDAD), bd("1"), bd("900.00"));

        assertThat(renglones.hayCambiosDePrecio()).isFalse();

        renglones.cambiarPrecio(renglon, bd("950"));

        assertThat(renglones.hayCambiosDePrecio()).isTrue();
    }

    @Test
    @DisplayName("Arma los renglones para el servicio con producto, cantidad y costo")
    void toDetalles() {
        renglones.agregar(producto(7L, "900", UnidadMedida.UNIDAD), bd("3"), bd("880"));

        assertThat(renglones.toDetalles())
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.getProductoId()).isEqualTo(7L);
                    assertThat(d.getCantidad()).isEqualByComparingTo("3");
                    assertThat(d.getPrecioCompra()).isEqualByComparingTo("880");
                })
                .isInstanceOf(DetalleCompraDto.class);
    }
}
