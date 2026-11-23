package org.gestionComercio.controller.facturacion;

import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.enums.EstadoComprobanteArca;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.util.dialog.Dialogs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FacturaElectronicaFormatoTest {

    private static ComprobanteArcaDto comprobante(TipoComprobante tipo, EstadoComprobanteArca estado) {
        return ComprobanteArcaDto.builder()
                .tipoComprobante(tipo)
                .estado(estado)
                .puntoVenta(3)
                .numero(estado == EstadoComprobanteArca.AUTORIZADO ? 12L : null)
                .cae(estado == EstadoComprobanteArca.AUTORIZADO ? "70000000000001" : null)
                .vencimientoCae(estado == EstadoComprobanteArca.AUTORIZADO ? LocalDate.of(2026, 12, 2) : null)
                .build();
    }

    private static VentaDto venta(ComprobanteArcaDto factura, ComprobanteArcaDto notaCredito) {
        return VentaDto.builder().factura(factura).notaCredito(notaCredito).build();
    }

    @Test
    @DisplayName("Tickets y remitos no tienen estado de factura electrónica")
    void ventaSinFactura() {
        assertThat(FacturaElectronicaFormato.estadoVenta(venta(null, null))).isNull();
        assertThat(FacturaElectronicaFormato.faltaAutorizar(venta(null, null))).isFalse();
    }

    @Test
    @DisplayName("El estado de la venta sigue a la factura y, si se anuló, a la nota de crédito")
    void estadoVenta() {
        ComprobanteArcaDto autorizada = comprobante(TipoComprobante.FACTURA_B, EstadoComprobanteArca.AUTORIZADO);

        assertThat(FacturaElectronicaFormato.estadoVenta(venta(autorizada, null))).isEqualTo("Facturada");
        assertThat(FacturaElectronicaFormato.estadoVenta(venta(
                comprobante(TipoComprobante.FACTURA_B, EstadoComprobanteArca.PENDIENTE), null)))
                .isEqualTo("Pendiente de CAE");
        assertThat(FacturaElectronicaFormato.estadoVenta(venta(
                comprobante(TipoComprobante.FACTURA_B, EstadoComprobanteArca.ENVIADO), null)))
                .isEqualTo("Pendiente de CAE");
        assertThat(FacturaElectronicaFormato.estadoVenta(venta(
                comprobante(TipoComprobante.FACTURA_B, EstadoComprobanteArca.RECHAZADO), null)))
                .isEqualTo("Rechazada");
        assertThat(FacturaElectronicaFormato.estadoVenta(venta(
                comprobante(TipoComprobante.FACTURA_B, EstadoComprobanteArca.CANCELADO), null)))
                .isEqualTo("No emitida");

        VentaDto ncPendiente = venta(autorizada,
                comprobante(TipoComprobante.NOTA_CREDITO_B, EstadoComprobanteArca.PENDIENTE));
        assertThat(FacturaElectronicaFormato.estadoVenta(ncPendiente)).isEqualTo("NC pendiente de CAE");
        assertThat(FacturaElectronicaFormato.faltaAutorizar(ncPendiente)).isTrue();

        VentaDto ncAutorizada = venta(autorizada,
                comprobante(TipoComprobante.NOTA_CREDITO_B, EstadoComprobanteArca.AUTORIZADO));
        assertThat(FacturaElectronicaFormato.estadoVenta(ncAutorizada)).isEqualTo("Anulada con NC");
        assertThat(FacturaElectronicaFormato.faltaAutorizar(ncAutorizada)).isFalse();
    }

    @Test
    @DisplayName("CAE con su vencimiento")
    void cae() {
        assertThat(FacturaElectronicaFormato.cae(comprobante(TipoComprobante.FACTURA_A, EstadoComprobanteArca.AUTORIZADO)))
                .isEqualTo("CAE 70000000000001 (vence el 02/12/2026)");
        assertThat(FacturaElectronicaFormato.cae(comprobante(TipoComprobante.FACTURA_A, EstadoComprobanteArca.PENDIENTE)))
                .isNull();
    }

    @Test
    @DisplayName("El resumen de la autorización distingue autorizado, rechazado y sin respuesta")
    void resultado() {
        var autorizado = FacturaElectronicaFormato.resultado(List.of(
                comprobante(TipoComprobante.FACTURA_B, EstadoComprobanteArca.AUTORIZADO)));
        assertThat(autorizado.tipo()).isEqualTo(Dialogs.Tipo.EXITO);
        assertThat(autorizado.todosAutorizados()).isTrue();
        assertThat(autorizado.mensaje()).contains("Factura B N° 0003-00000012").contains("CAE 70000000000001");

        ComprobanteArcaDto rechazo = comprobante(TipoComprobante.FACTURA_A, EstadoComprobanteArca.RECHAZADO);
        rechazo.setObservaciones("10242 - Falta la condición frente al IVA");
        var rechazado = FacturaElectronicaFormato.resultado(List.of(rechazo));
        assertThat(rechazado.tipo()).isEqualTo(Dialogs.Tipo.ADVERTENCIA);
        assertThat(rechazado.titulo()).contains("rechazó");
        assertThat(rechazado.mensaje()).contains("10242");

        var sinRespuesta = FacturaElectronicaFormato.resultado(List.of(
                comprobante(TipoComprobante.FACTURA_B, EstadoComprobanteArca.ENVIADO)));
        assertThat(sinRespuesta.titulo()).isEqualTo("Comprobante pendiente de CAE");
        assertThat(sinRespuesta.mensaje()).contains("no duplicarlo");
    }

}
