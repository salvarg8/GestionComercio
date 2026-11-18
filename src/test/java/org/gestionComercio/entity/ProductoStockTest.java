package org.gestionComercio.entity;

import org.gestionComercio.enums.TipoMovimientoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductoStockTest {

    private Producto producto;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        producto = Producto.builder().id(1L).codigo("P001").nombre("Yerba").stock(BigDecimal.TEN).build();
        usuario = Usuario.builder().id(5L).usuario("admin").nombre("Ana").apellido("Pérez").build();
    }

    private MovimientoInventario movimiento(Producto p, TipoMovimientoInventario tipo, int cantidad) {
        return MovimientoInventario.nuevo().producto(p).tipo(tipo).cantidad(BigDecimal.valueOf(cantidad))
                .motivo("Motivo").usuario(usuario).registrar();
    }

    @Test
    void aplicarMovimientoActualizaElStockConElResultado() {

        producto.aplicarMovimiento(movimiento(producto, TipoMovimientoInventario.SALIDA, -3));

        assertThat(producto.getStock()).isEqualByComparingTo("7");
    }

    @Test
    void aplicarMovimientoComparaPorValorSinImportarLaEscala() {

        // El stock leído de la base puede venir sin decimales (10) y el movimiento con escala 3 (10.000).
        MovimientoInventario m = movimiento(producto, TipoMovimientoInventario.ENTRADA, 1);
        assertThat(m.getStockAnterior().scale()).isEqualTo(3);

        producto.aplicarMovimiento(m);

        assertThat(producto.getStock()).isEqualByComparingTo("11");
    }

    @Test
    void aplicarMovimientoDeOtroProductoFalla() {

        Producto otro = Producto.builder().id(2L).codigo("P002").nombre("Azúcar").stock(BigDecimal.TEN).build();
        MovimientoInventario m = movimiento(otro, TipoMovimientoInventario.ENTRADA, 3);

        assertThatThrownBy(() -> producto.aplicarMovimiento(m))
                .isInstanceOf(IllegalStateException.class);
        assertThat(producto.getStock()).isEqualByComparingTo("10");
    }

    @Test
    void aplicarDosVecesElMismoMovimientoFalla() {

        MovimientoInventario m = movimiento(producto, TipoMovimientoInventario.ENTRADA, 3);

        producto.aplicarMovimiento(m);

        assertThatThrownBy(() -> producto.aplicarMovimiento(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cambió");
        assertThat(producto.getStock()).isEqualByComparingTo("13");
    }

    @Test
    void inicializarStockSoloEnProductosNuevos() {

        Producto nuevo = Producto.builder().stock(new BigDecimal("99")).build();
        nuevo.inicializarStock();
        assertThat(nuevo.getStock()).isZero();

        assertThatThrownBy(() -> producto.inicializarStock())
                .isInstanceOf(IllegalStateException.class);
    }

}
