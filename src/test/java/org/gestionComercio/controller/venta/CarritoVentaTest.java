package org.gestionComercio.controller.venta;

import org.gestionComercio.controller.venta.CarritoVenta.Cobro;
import org.gestionComercio.controller.venta.CarritoVenta.Renglon;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.enums.UnidadMedida;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CarritoVentaTest {

    private final CarritoVenta carrito = new CarritoVenta();

    private static ProductoDto producto(Long id, String precio, String stock, UnidadMedida unidad) {
        return ProductoDto.builder()
                .id(id).codigo("P" + id).nombre("Producto " + id)
                .precioVenta(new BigDecimal(precio))
                .stock(new BigDecimal(stock))
                .manejaStock(true)
                .permiteStockNegativo(false)
                .unidadMedida(unidad)
                .build();
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    // ------------------------------------------------------------------
    // Renglones
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Agregar el mismo producto suma la cantidad a su renglón")
    void agregarMismoProductoSuma() {
        ProductoDto gaseosa = producto(1L, "1500", "10", UnidadMedida.UNIDAD);

        Renglon primero = carrito.agregar(gaseosa, bd("2"));
        Renglon segundo = carrito.agregar(gaseosa, bd("3"));

        assertThat(segundo).isSameAs(primero);
        assertThat(carrito.getRenglones()).hasSize(1);
        assertThat(primero.getCantidad()).isEqualByComparingTo("5");
        assertThat(primero.getSubtotal()).isEqualByComparingTo("7500");
    }

    @Test
    @DisplayName("El subtotal del renglón se redondea a dos decimales como en el servicio")
    void subtotalRedondeado() {
        ProductoDto queso = producto(2L, "7999.99", "10", UnidadMedida.KILOGRAMO);

        Renglon renglon = carrito.agregar(queso, bd("0.333"));

        // 0,333 × 7999,99 = 2663,99667 → 2664,00
        assertThat(renglon.getSubtotal()).isEqualByComparingTo("2664.00");
    }

    @Test
    @DisplayName("Rechaza cantidades inválidas sin modificar el carrito")
    void cantidadesInvalidas() {
        ProductoDto gaseosa = producto(1L, "1500", "10", UnidadMedida.UNIDAD);
        ProductoDto queso = producto(2L, "8000", "10", UnidadMedida.KILOGRAMO);

        assertThatThrownBy(() -> carrito.agregar(gaseosa, bd("0"))).hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> carrito.agregar(gaseosa, bd("1.5"))).hasMessageContaining("número entero");
        assertThatThrownBy(() -> carrito.agregar(queso, bd("0.1234"))).hasMessageContaining("decimales");
        assertThatThrownBy(() -> carrito.agregar(gaseosa, null)).hasMessageContaining("cantidad");

        assertThat(carrito.estaVacio()).isTrue();
    }

    @Test
    @DisplayName("cambiarCantidad() valida y reemplaza; sumar() no baja de uno")
    void cambiarYSumar() {
        Renglon renglon = carrito.agregar(producto(1L, "100", "10", UnidadMedida.UNIDAD), bd("1"));

        carrito.cambiarCantidad(renglon, bd("4"));
        assertThat(renglon.getCantidad()).isEqualByComparingTo("4");

        assertThatThrownBy(() -> carrito.cambiarCantidad(renglon, bd("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(renglon.getCantidad()).isEqualByComparingTo("4");

        assertThat(carrito.sumar(renglon, bd("1"))).isTrue();
        assertThat(renglon.getCantidad()).isEqualByComparingTo("5");

        carrito.cambiarCantidad(renglon, bd("1"));
        assertThat(carrito.sumar(renglon, bd("-1"))).isFalse();
        assertThat(renglon.getCantidad()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("Quitar y vaciar")
    void quitarYVaciar() {
        Renglon a = carrito.agregar(producto(1L, "100", "10", UnidadMedida.UNIDAD), bd("1"));
        carrito.agregar(producto(2L, "200", "10", UnidadMedida.UNIDAD), bd("1"));

        carrito.quitar(a);
        assertThat(carrito.getRenglones()).extracting(r -> r.getProducto().getId()).containsExactly(2L);

        carrito.vaciar();
        assertThat(carrito.estaVacio()).isTrue();
        assertThat(carrito.subtotal()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Marca los renglones que superan el stock, salvo sin control o con negativo permitido")
    void sinStockSuficiente() {
        ProductoDto conStock = producto(1L, "100", "2", UnidadMedida.UNIDAD);
        ProductoDto sinControl = producto(2L, "100", "0", UnidadMedida.UNIDAD);
        sinControl.setManejaStock(false);
        ProductoDto negativoPermitido = producto(3L, "100", "0", UnidadMedida.UNIDAD);
        negativoPermitido.setPermiteStockNegativo(true);

        Renglon justo = carrito.agregar(conStock, bd("2"));
        carrito.agregar(sinControl, bd("5"));
        carrito.agregar(negativoPermitido, bd("5"));

        assertThat(carrito.sinStockSuficiente()).isEmpty();

        carrito.sumar(justo, BigDecimal.ONE);

        assertThat(carrito.sinStockSuficiente()).containsExactly(justo);
    }

    @Test
    @DisplayName("toDetalles() envía sólo producto y cantidad")
    void toDetalles() {
        carrito.agregar(producto(1L, "100", "10", UnidadMedida.UNIDAD), bd("2"));
        carrito.agregar(producto(2L, "8000", "10", UnidadMedida.KILOGRAMO), bd("0.5"));

        assertThat(carrito.toDetalles())
                .extracting(DetalleVentaDto::getProductoId, d -> d.getCantidad().stripTrailingZeros().toPlainString(),
                        DetalleVentaDto::getPrecioUnitario)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, "2", null),
                        org.assertj.core.groups.Tuple.tuple(2L, "0.5", null));
    }

    // ------------------------------------------------------------------
    // Cobro
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Sin descuento ni paga con: total igual al subtotal y sin vuelto")
    void cobroSimple() {
        Cobro cobro = CarritoVenta.cobrar(bd("3000.00"), "", true, "");

        assertThat(cobro.valido()).isTrue();
        assertThat(cobro.descuento()).isEqualByComparingTo("0");
        assertThat(cobro.total()).isEqualByComparingTo("3000");
        assertThat(cobro.vuelto()).isNull();
    }

    @Test
    @DisplayName("Calcula el vuelto en efectivo con descuento en formato argentino")
    void cobroConVuelto() {
        Cobro cobro = CarritoVenta.cobrar(bd("3000.00"), "250,50", true, "$ 5.000,00");

        assertThat(cobro.valido()).isTrue();
        assertThat(cobro.total()).isEqualByComparingTo("2749.50");
        assertThat(cobro.vuelto()).isEqualByComparingTo("2250.50");
    }

    @Test
    @DisplayName("Paga con menor que el total es un error")
    void pagaConInsuficiente() {
        Cobro cobro = CarritoVenta.cobrar(bd("3000.00"), "", true, "2000");

        assertThat(cobro.valido()).isFalse();
        assertThat(cobro.error()).contains("faltan").contains("1.000");
    }

    @Test
    @DisplayName("Con otra forma de pago se ignora el paga con")
    void otraFormaDePago() {
        Cobro cobro = CarritoVenta.cobrar(bd("3000.00"), "", false, "abc");

        assertThat(cobro.valido()).isTrue();
        assertThat(cobro.vuelto()).isNull();
        assertThat(cobro.pagaCon()).isNull();
    }

    @Test
    @DisplayName("Descuento inválido o mayor que el subtotal es un error")
    void descuentoInvalido() {
        assertThat(CarritoVenta.cobrar(bd("100"), "abc", true, "").error()).contains("importe válido");
        assertThat(CarritoVenta.cobrar(bd("100"), "-5", true, "").error()).contains("negativo");
        assertThat(CarritoVenta.cobrar(bd("100"), "1,234", true, "").error()).contains("dos decimales");
        assertThat(CarritoVenta.cobrar(bd("100"), "100,01", true, "").error()).contains("no puede superar");
        assertThat(CarritoVenta.cobrar(bd("100"), "100", true, "").valido()).isTrue();
    }

}
