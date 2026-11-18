package org.gestionComercio.entity;

import org.gestionComercio.enums.TipoMovimientoInventario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovimientoInventarioTest {

    private Producto producto;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        producto = Producto.builder().id(1L).codigo("P001").nombre("Yerba").stock(10).build();
        usuario = Usuario.builder().id(5L).usuario("admin").nombre("Ana").apellido("Pérez").build();
    }

    @Test
    void registrarEntradaCalculaStockResultanteDesdeElStockDelProducto() {

        LocalDateTime antes = LocalDateTime.now();

        MovimientoInventario m = MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.ENTRADA, 5, "  Compra a proveedor  ", usuario);

        assertThat(m.getProducto()).isSameAs(producto);
        assertThat(m.getUsuario()).isSameAs(usuario);
        assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.ENTRADA);
        assertThat(m.getCantidad()).isEqualTo(5);
        assertThat(m.getStockAnterior()).isEqualTo(10);
        assertThat(m.getStockResultante()).isEqualTo(15);
        assertThat(m.getMotivo()).isEqualTo("Compra a proveedor");
        assertThat(m.getFecha()).isAfterOrEqualTo(antes).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void registrarNoModificaElStockDelProducto() {

        MovimientoInventario.registrar(producto, TipoMovimientoInventario.SALIDA, -3, "Venta", usuario);

        assertThat(producto.getStock()).isEqualTo(10);
    }

    @Test
    void registrarSalidaPuedeDejarStockNegativo() {

        MovimientoInventario m = MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.SALIDA, -12, "Venta", usuario);

        assertThat(m.getStockResultante()).isEqualTo(-2);
    }

    @Test
    void registrarConProductoSinStockTomaCeroComoStockAnterior() {

        producto.setStock(null);

        MovimientoInventario m = MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.AJUSTE, 4, "Recuento inicial", usuario);

        assertThat(m.getStockAnterior()).isZero();
        assertThat(m.getStockResultante()).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({
            "ENTRADA, 0, La cantidad de una entrada debe ser positiva.",
            "ENTRADA, -1, La cantidad de una entrada debe ser positiva.",
            "SALIDA, 0, La cantidad de una salida debe ser negativa.",
            "SALIDA, 1, La cantidad de una salida debe ser negativa.",
            "AJUSTE, 0, La cantidad de un ajuste no puede ser cero."
    })
    void rechazaCantidadConSignoInvalidoParaElTipo(TipoMovimientoInventario tipo, int cantidad, String mensaje) {

        assertThatThrownBy(() -> MovimientoInventario.registrar(producto, tipo, cantidad, "Motivo", usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(mensaje);
    }

    @Test
    void ajusteAdmiteCantidadPositivaONegativa() {

        assertThat(MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.AJUSTE, -4, "Rotura", usuario).getStockResultante())
                .isEqualTo(6);

        assertThat(MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.AJUSTE, 4, "Sobrante", usuario).getStockResultante())
                .isEqualTo(14);
    }

    @Test
    void exigeProductoUsuarioYTipo() {

        assertThatThrownBy(() -> MovimientoInventario.registrar(
                null, TipoMovimientoInventario.ENTRADA, 1, "Motivo", usuario))
                .hasMessage("El producto es obligatorio.");

        assertThatThrownBy(() -> MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.ENTRADA, 1, "Motivo", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El usuario responsable es obligatorio.");

        assertThatThrownBy(() -> MovimientoInventario.registrar(producto, null, 1, "Motivo", usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El tipo de movimiento es obligatorio.");
    }

    @Test
    void exigeMotivoNoVacioYDentroDelLargoMaximo() {

        assertThatThrownBy(() -> MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.ENTRADA, 1, "   ", usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El motivo es obligatorio.");

        assertThatThrownBy(() -> MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.ENTRADA, 1, null, usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El motivo es obligatorio.");

        String largo = "x".repeat(MovimientoInventario.MOTIVO_MAX + 1);

        assertThatThrownBy(() -> MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.ENTRADA, 1, largo, usuario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede superar");
    }

    @Test
    void rechazaDesbordeDeStock() {

        producto.setStock(Integer.MAX_VALUE);

        assertThatThrownBy(() -> MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.ENTRADA, 1, "Compra", usuario))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void unMovimientoGuardadoNoSePuedeModificarNiBorrar() {

        MovimientoInventario m = MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.ENTRADA, 1, "Compra", usuario);

        assertThatThrownBy(m::impedirModificacion)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no se pueden modificar");

        assertThatThrownBy(m::impedirBorrado)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no se pueden eliminar");
    }

    @Test
    void verificarConsistenciaPasaParaUnMovimientoRegistrado() {

        MovimientoInventario m = MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.SALIDA, -1, "Venta", usuario);

        m.verificarConsistencia();
    }
}
