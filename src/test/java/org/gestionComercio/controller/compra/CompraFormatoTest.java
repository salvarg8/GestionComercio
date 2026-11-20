package org.gestionComercio.controller.compra;

import org.gestionComercio.enums.EstadoCompra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompraFormatoTest {

    @Test
    @DisplayName("Sólo las compras pendientes o recibidas se pueden anular; sólo las pendientes, recibir")
    void accionesPorEstado() {
        assertThat(CompraFormato.anulable(EstadoCompra.PENDIENTE)).isTrue();
        assertThat(CompraFormato.anulable(EstadoCompra.RECIBIDA)).isTrue();
        assertThat(CompraFormato.anulable(EstadoCompra.ANULADA)).isFalse();
        assertThat(CompraFormato.anulable(EstadoCompra.CANCELADA)).isFalse();

        assertThat(CompraFormato.recibible(EstadoCompra.PENDIENTE)).isTrue();
        assertThat(CompraFormato.recibible(EstadoCompra.RECIBIDA)).isFalse();
    }

    @Test
    @DisplayName("Las compras anuladas o canceladas no suman al importe comprado")
    void sumaAlImporte() {
        assertThat(CompraFormato.sumaAlImporte(EstadoCompra.RECIBIDA)).isTrue();
        assertThat(CompraFormato.sumaAlImporte(EstadoCompra.PENDIENTE)).isTrue();
        assertThat(CompraFormato.sumaAlImporte(EstadoCompra.ANULADA)).isFalse();
        assertThat(CompraFormato.sumaAlImporte(EstadoCompra.CANCELADA)).isFalse();
    }

    @Test
    @DisplayName("Los efectos de anular dependen de si se recibió y de si se pagó en efectivo")
    void efectosAnulacion() {
        assertThat(CompraFormato.efectosAnulacion(EstadoCompra.RECIBIDA, true, new BigDecimal("9000")))
                .contains("Se descontará del stock")
                .contains("ingreso de")
                .contains("reintegro");

        assertThat(CompraFormato.efectosAnulacion(EstadoCompra.PENDIENTE, false, new BigDecimal("9000")))
                .contains("el stock no cambia")
                .contains("No se registra ningún movimiento de caja");
    }

    @Test
    @DisplayName("Factura vacía se muestra como \"Sin factura\"")
    void factura() {
        assertThat(CompraFormato.factura(null)).isEqualTo("Sin factura");
        assertThat(CompraFormato.factura("  ")).isEqualTo("Sin factura");
        assertThat(CompraFormato.factura("A-1")).isEqualTo("A-1");
    }

    @Test
    @DisplayName("El usuario se muestra como \"Nombre (login)\"")
    void usuario() {
        assertThat(CompraFormato.usuario("Ana Pérez", "ana")).isEqualTo("Ana Pérez (ana)");
        assertThat(CompraFormato.usuario(null, "ana")).isEqualTo("ana");
    }

    @Test
    @DisplayName("Lee importes vacíos como null y rechaza los inválidos")
    void leerImporte() {
        assertThat(CompraFormato.leerImporte("", "El costo")).isNull();
        assertThat(CompraFormato.leerImporte("1250,50", "El costo")).isEqualByComparingTo("1250.50");

        assertThatThrownBy(() -> CompraFormato.leerImporte("abc", "El costo")).hasMessageContaining("importe válido");
        assertThatThrownBy(() -> CompraFormato.leerImporte("-1", "El costo")).hasMessageContaining("negativo");
        assertThatThrownBy(() -> CompraFormato.leerImporte("1,555", "El costo")).hasMessageContaining("dos decimales");
    }
}
