package org.gestionComercio.controller.venta;

import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VentaFormatoTest {

    @Test
    @DisplayName("usuario() muestra nombre y login, o sólo el login")
    void usuario() {
        assertThat(VentaFormato.usuario("Ana Pérez", "ana")).isEqualTo("Ana Pérez (ana)");
        assertThat(VentaFormato.usuario("  ", "ana")).isEqualTo("ana");
        assertThat(VentaFormato.usuario(null, null)).isEmpty();
    }

    @Test
    @DisplayName("Cada estado tiene su estilo")
    void estiloEstado() {
        assertThat(VentaFormato.estiloEstado(EstadoVenta.PAGADA)).isEqualTo("estado-activo");
        assertThat(VentaFormato.estiloEstado(EstadoVenta.PENDIENTE)).isEqualTo("movimiento-ajuste");
        assertThat(VentaFormato.estiloEstado(EstadoVenta.CANCELADA)).isEqualTo("stock-bajo");
        assertThat(VentaFormato.estiloEstado(EstadoVenta.ANULADA)).isEqualTo("stock-bajo");
    }

    @Test
    @DisplayName("Sólo se anulan ventas pagadas o pendientes, y las anuladas no suman al importe")
    void anulacion() {
        assertThat(VentaFormato.anulable(EstadoVenta.PAGADA)).isTrue();
        assertThat(VentaFormato.anulable(EstadoVenta.PENDIENTE)).isTrue();
        assertThat(VentaFormato.anulable(EstadoVenta.ANULADA)).isFalse();
        assertThat(VentaFormato.anulable(EstadoVenta.CANCELADA)).isFalse();

        assertThat(VentaFormato.sumaAlImporte(EstadoVenta.PAGADA)).isTrue();
        assertThat(VentaFormato.sumaAlImporte(EstadoVenta.ANULADA)).isFalse();
        assertThat(VentaFormato.sumaAlImporte(EstadoVenta.CANCELADA)).isFalse();
    }

    @Test
    @DisplayName("efectosAnulacion() avisa del egreso de caja sólo en ventas en efectivo")
    void efectosAnulacion() {
        assertThat(VentaFormato.efectosAnulacion(FormaPago.EFECTIVO, new BigDecimal("1500")))
                .contains("vuelven al stock").contains("egreso de " + VentaFormato.importe(new BigDecimal("1500")));
        assertThat(VentaFormato.efectosAnulacion(FormaPago.TARJETA_DEBITO, new BigDecimal("1500")))
                .contains("vuelven al stock").contains("ningún movimiento de caja");
    }

    @Test
    @DisplayName("leerImporte() acepta vacío y formatos comunes, y rechaza lo inválido")
    void leerImporte() {
        assertThat(VentaFormato.leerImporte("", "X")).isNull();
        assertThat(VentaFormato.leerImporte("$ 1.234,50", "X")).isEqualByComparingTo("1234.50");
        assertThatThrownBy(() -> VentaFormato.leerImporte("99999999999", "X")).hasMessageContaining("máximo");
    }

}
