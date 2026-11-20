package org.gestionComercio.controller.venta;

import org.gestionComercio.enums.EstadoVenta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VentaFormatoTest {

    @Test
    @DisplayName("numero() completa con ceros")
    void numero() {
        assertThat(VentaFormato.numero(42L)).isEqualTo("N° 000042");
        assertThat(VentaFormato.numero(null)).isEmpty();
    }

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
    }

    @Test
    @DisplayName("leerImporte() acepta vacío y formatos comunes, y rechaza lo inválido")
    void leerImporte() {
        assertThat(VentaFormato.leerImporte("", "X")).isNull();
        assertThat(VentaFormato.leerImporte("$ 1.234,50", "X")).isEqualByComparingTo("1234.50");
        assertThatThrownBy(() -> VentaFormato.leerImporte("99999999999", "X")).hasMessageContaining("máximo");
    }

}
