package org.gestionComercio.entity;

import org.gestionComercio.enums.MotivoAjusteInventario;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.enums.UnidadMedida;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovimientoInventarioTest {

    private Producto producto;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        producto = producto(new BigDecimal("10"), UnidadMedida.UNIDAD);
        usuario = Usuario.builder().id(5L).usuario("admin").nombre("Ana").apellido("Pérez").build();
    }

    private static Producto producto(BigDecimal stock, UnidadMedida unidad) {
        return Producto.builder().id(1L).codigo("P001").nombre("Yerba").stock(stock).unidadMedida(unidad).build();
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private MovimientoInventario.MovimientoInventarioBuilder base(TipoMovimientoInventario tipo, String cantidad) {
        return MovimientoInventario.nuevo()
                .producto(producto)
                .tipo(tipo)
                .cantidad(bd(cantidad))
                .motivo("Motivo")
                .motivoAjuste(tipo == TipoMovimientoInventario.AJUSTE ? MotivoAjusteInventario.RECUENTO : null)
                .usuario(usuario);
    }

    @Test
    void registrarEntradaCalculaStockResultanteDesdeElStockDelProducto() {

        LocalDateTime antes = LocalDateTime.now();

        MovimientoInventario m = base(TipoMovimientoInventario.ENTRADA, "5").motivo("  Compra a proveedor  ").registrar();

        assertThat(m.getProducto()).isSameAs(producto);
        assertThat(m.getUsuario()).isSameAs(usuario);
        assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.ENTRADA);
        assertThat(m.getCantidad()).isEqualByComparingTo("5");
        assertThat(m.getStockAnterior()).isEqualByComparingTo("10");
        assertThat(m.getStockResultante()).isEqualByComparingTo("15");
        assertThat(m.getMotivo()).isEqualTo("Compra a proveedor");
        assertThat(m.getFecha()).isAfterOrEqualTo(antes).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void lasCantidadesQuedanConTresDecimales() {

        MovimientoInventario m = base(TipoMovimientoInventario.ENTRADA, "5").registrar();

        assertThat(m.getCantidad().scale()).isEqualTo(3);
        assertThat(m.getStockAnterior().scale()).isEqualTo(3);
        assertThat(m.getStockResultante().scale()).isEqualTo(3);
    }

    @Test
    void porDefectoEsManualYSinComprobante() {

        MovimientoInventario m = base(TipoMovimientoInventario.ENTRADA, "1").registrar();

        assertThat(m.getOrigen()).isEqualTo(OrigenMovimientoInventario.MANUAL);
        assertThat(m.getOrigenId()).isNull();
        assertThat(m.esReversion()).isFalse();
    }

    @Test
    void registrarNoModificaElStockDelProducto() {

        base(TipoMovimientoInventario.SALIDA, "-3").registrar();

        assertThat(producto.getStock()).isEqualByComparingTo("10");
    }

    @Test
    void registrarSalidaPuedeDejarStockNegativo() {

        MovimientoInventario m = base(TipoMovimientoInventario.SALIDA, "-12").registrar();

        assertThat(m.getStockResultante()).isEqualByComparingTo("-2");
    }

    @Test
    void registrarConProductoSinStockTomaCeroComoStockAnterior() {

        producto = producto(null, UnidadMedida.UNIDAD);

        MovimientoInventario m = base(TipoMovimientoInventario.AJUSTE, "4").registrar();

        assertThat(m.getStockAnterior()).isZero();
        assertThat(m.getStockResultante()).isEqualByComparingTo("4");
    }

    @ParameterizedTest
    @CsvSource({
            "ENTRADA, 0, La cantidad de una entrada debe ser positiva.",
            "ENTRADA, -1, La cantidad de una entrada debe ser positiva.",
            "SALIDA, 0, La cantidad de una salida debe ser negativa.",
            "SALIDA, 1, La cantidad de una salida debe ser negativa.",
            "AJUSTE, 0, La cantidad de un ajuste no puede ser cero."
    })
    void rechazaCantidadConSignoInvalidoParaElTipo(TipoMovimientoInventario tipo, String cantidad, String mensaje) {

        assertThatThrownBy(() -> base(tipo, cantidad).registrar())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(mensaje);
    }

    @Test
    void ajusteAdmiteCantidadPositivaONegativa() {

        assertThat(base(TipoMovimientoInventario.AJUSTE, "-4").registrar().getStockResultante())
                .isEqualByComparingTo("6");

        assertThat(base(TipoMovimientoInventario.AJUSTE, "4").registrar().getStockResultante())
                .isEqualByComparingTo("14");
    }

    @Test
    void elAjusteExigeCausaYLosDemasNoLaAdmiten() {

        assertThatThrownBy(() -> base(TipoMovimientoInventario.AJUSTE, "1").motivoAjuste(null).registrar())
                .hasMessage("Debe indicar la causa del ajuste.");

        assertThatThrownBy(() -> base(TipoMovimientoInventario.ENTRADA, "1")
                .motivoAjuste(MotivoAjusteInventario.ROTURA).registrar())
                .hasMessage("Sólo los ajustes indican una causa de ajuste.");
    }

    @Test
    void unidadNoFraccionableRechazaDecimales() {

        assertThatThrownBy(() -> base(TipoMovimientoInventario.ENTRADA, "1.5").registrar())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no admite cantidades con decimales");

        // 2,000 es entero aunque tenga escala.
        assertThat(base(TipoMovimientoInventario.ENTRADA, "2.000").registrar().getStockResultante())
                .isEqualByComparingTo("12");
    }

    @Test
    void unidadFraccionableAdmiteHastaTresDecimales() {

        producto = producto(bd("1.250"), UnidadMedida.KILOGRAMO);

        MovimientoInventario m = base(TipoMovimientoInventario.SALIDA, "-0.75").registrar();

        assertThat(m.getStockResultante()).isEqualByComparingTo("0.5");

        assertThatThrownBy(() -> base(TipoMovimientoInventario.SALIDA, "-0.0001").registrar())
                .hasMessageContaining("como máximo 3 decimales");
    }

    @Test
    void unMovimientoDeComprobanteExigeSuId() {

        MovimientoInventario m = base(TipoMovimientoInventario.SALIDA, "-1")
                .origen(OrigenMovimientoInventario.VENTA).origenId(40L).registrar();

        assertThat(m.getOrigen()).isEqualTo(OrigenMovimientoInventario.VENTA);
        assertThat(m.getOrigenId()).isEqualTo(40L);

        assertThatThrownBy(() -> base(TipoMovimientoInventario.SALIDA, "-1")
                .origen(OrigenMovimientoInventario.VENTA).registrar())
                .hasMessage("Un movimiento de venta debe indicar el comprobante.");

        assertThatThrownBy(() -> base(TipoMovimientoInventario.SALIDA, "-1").origenId(40L).registrar())
                .hasMessage("Un movimiento manual no tiene comprobante.");
    }

    @Test
    void revertirCreaElMovimientoOpuestoDelMismoComprobante() {

        MovimientoInventario venta = base(TipoMovimientoInventario.SALIDA, "-3")
                .origen(OrigenMovimientoInventario.VENTA).origenId(40L).registrar();
        producto.aplicarMovimiento(venta);

        MovimientoInventario reversion = MovimientoInventario.revertir(venta, "Anulación", usuario);

        assertThat(reversion.getTipo()).isEqualTo(TipoMovimientoInventario.ENTRADA);
        assertThat(reversion.getCantidad()).isEqualByComparingTo("3");
        assertThat(reversion.getStockAnterior()).isEqualByComparingTo("7");
        assertThat(reversion.getStockResultante()).isEqualByComparingTo("10");
        assertThat(reversion.getOrigen()).isEqualTo(OrigenMovimientoInventario.VENTA);
        assertThat(reversion.getOrigenId()).isEqualTo(40L);
        assertThat(reversion.getMovimientoRevertido()).isSameAs(venta);
        assertThat(reversion.esReversion()).isTrue();
    }

    @Test
    void revertirUnAjusteConservaLaCausa() {

        MovimientoInventario ajuste = base(TipoMovimientoInventario.AJUSTE, "-2")
                .motivoAjuste(MotivoAjusteInventario.ROTURA).registrar();

        MovimientoInventario reversion = MovimientoInventario.revertir(ajuste, "Error de carga", usuario);

        assertThat(reversion.getTipo()).isEqualTo(TipoMovimientoInventario.AJUSTE);
        assertThat(reversion.getCantidad()).isEqualByComparingTo("2");
        assertThat(reversion.getMotivoAjuste()).isEqualTo(MotivoAjusteInventario.ROTURA);
    }

    @Test
    void noSePuedeRevertirUnaReversion() {

        MovimientoInventario original = base(TipoMovimientoInventario.ENTRADA, "1")
                .origen(OrigenMovimientoInventario.COMPRA).origenId(1L).registrar();
        MovimientoInventario reversion = MovimientoInventario.revertir(original, "Anulación", usuario);

        assertThatThrownBy(() -> MovimientoInventario.revertir(reversion, "Otra", usuario))
                .hasMessage("No se puede revertir una reversión.");
    }

    @Test
    void exigeProductoUsuarioYTipo() {

        assertThatThrownBy(() -> base(TipoMovimientoInventario.ENTRADA, "1").producto(null).registrar())
                .hasMessage("El producto es obligatorio.");

        assertThatThrownBy(() -> base(TipoMovimientoInventario.ENTRADA, "1").usuario(null).registrar())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El usuario responsable es obligatorio.");

        assertThatThrownBy(() -> base(TipoMovimientoInventario.ENTRADA, "1").tipo(null).registrar())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El tipo de movimiento es obligatorio.");

        assertThatThrownBy(() -> base(TipoMovimientoInventario.ENTRADA, "1").cantidad(null).registrar())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La cantidad es obligatoria.");
    }

    @Test
    void exigeMotivoNoVacioYDentroDelLargoMaximo() {

        assertThatThrownBy(() -> base(TipoMovimientoInventario.ENTRADA, "1").motivo("   ").registrar())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El motivo es obligatorio.");

        assertThatThrownBy(() -> base(TipoMovimientoInventario.ENTRADA, "1").motivo(null).registrar())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El motivo es obligatorio.");

        String largo = "x".repeat(MovimientoInventario.MOTIVO_MAX + 1);

        assertThatThrownBy(() -> base(TipoMovimientoInventario.ENTRADA, "1").motivo(largo).registrar())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede superar");
    }

    @Test
    void rechazaCantidadesFueraDelMaximo() {

        producto = producto(bd("999999999999"), UnidadMedida.UNIDAD);

        assertThatThrownBy(() -> base(TipoMovimientoInventario.ENTRADA, "1").registrar())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La cantidad supera el máximo admitido.");
    }

    @Test
    void unMovimientoGuardadoNoSePuedeModificarNiBorrar() {

        MovimientoInventario m = base(TipoMovimientoInventario.ENTRADA, "1").registrar();

        assertThatThrownBy(m::impedirModificacion)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no se pueden modificar");

        assertThatThrownBy(m::impedirBorrado)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no se pueden eliminar");
    }

    @Test
    void verificarConsistenciaPasaParaUnMovimientoRegistrado() {

        MovimientoInventario m = base(TipoMovimientoInventario.SALIDA, "-1").registrar();

        m.verificarConsistencia();
    }
}
