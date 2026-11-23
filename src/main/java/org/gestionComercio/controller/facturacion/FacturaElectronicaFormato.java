package org.gestionComercio.controller.facturacion;

import javafx.scene.control.Label;
import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.enums.EstadoComprobanteArca;
import org.gestionComercio.util.dialog.Dialogs;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Textos y etiquetas del estado de las facturas electrónicas que comparten
 * el punto de venta, el listado de ventas y los comprobantes pendientes. Las
 * funciones de texto no dependen del toolkit de JavaFX.
 */
public final class FacturaElectronicaFormato {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Qué mostrarle al usuario después de pedir el CAE. */
    public record Resultado(Dialogs.Tipo tipo, String titulo, String mensaje, boolean todosAutorizados) {
    }

    private FacturaElectronicaFormato() {
    }

    /**
     * El comprobante electrónico que define el estado de la venta: la nota de
     * crédito si se anuló y todavía falta autorizarla; si no, la factura.
     *
     * @return null si la venta no es una factura electrónica
     */
    public static ComprobanteArcaDto comprobanteDeReferencia(VentaDto venta) {

        if (venta == null || venta.getFactura() == null) {
            return null;
        }

        ComprobanteArcaDto nc = venta.getNotaCredito();

        return nc != null && nc.getEstado() != null && nc.getEstado().faltaAutorizar() ? nc : venta.getFactura();
    }

    /** @return true si a la venta le falta el CAE de la factura o de su nota de crédito. */
    public static boolean faltaAutorizar(VentaDto venta) {

        if (venta == null) {
            return false;
        }

        return faltaAutorizar(venta.getFactura()) || faltaAutorizar(venta.getNotaCredito());
    }

    public static boolean faltaAutorizar(ComprobanteArcaDto comprobante) {
        return comprobante != null && comprobante.getEstado() != null && comprobante.getEstado().faltaAutorizar();
    }

    /**
     * Estado de la factura para el listado de ventas: "Facturada", "Pendiente
     * de CAE", "Rechazada", ... o null si la venta no es una factura electrónica.
     */
    public static String estadoVenta(VentaDto venta) {

        ComprobanteArcaDto comprobante = comprobanteDeReferencia(venta);

        if (comprobante == null || comprobante.getEstado() == null) {
            return null;
        }

        boolean nc = comprobante == venta.getNotaCredito();

        return switch (comprobante.getEstado()) {
            case AUTORIZADO -> venta.getNotaCredito() != null ? "Anulada con NC" : "Facturada";
            case PENDIENTE, ENVIADO -> nc ? "NC pendiente de CAE" : "Pendiente de CAE";
            case RECHAZADO -> nc ? "NC rechazada" : "Rechazada";
            case CANCELADO -> "No emitida";
        };
    }

    public static String estilo(EstadoComprobanteArca estado) {

        if (estado == null) {
            return "estado-inactivo";
        }

        return switch (estado) {
            case AUTORIZADO -> "estado-activo";
            case PENDIENTE, ENVIADO -> "factura-pendiente";
            case RECHAZADO -> "stock-bajo";
            case CANCELADO -> "estado-inactivo";
        };
    }

    /** Etiqueta del estado de la factura de la venta, o null si no es una factura electrónica. */
    public static Label badgeVenta(VentaDto venta) {

        String texto = estadoVenta(venta);

        if (texto == null) {
            return null;
        }

        return badge(texto, estilo(comprobanteDeReferencia(venta).getEstado()));
    }

    /** Etiqueta con la descripción del estado del comprobante. */
    public static Label badge(ComprobanteArcaDto comprobante) {
        EstadoComprobanteArca estado = comprobante == null ? null : comprobante.getEstado();
        return badge(estado == null ? "" : estado.getDescripcion(), estilo(estado));
    }

    private static Label badge(String texto, String estilo) {
        Label badge = new Label(texto);
        badge.getStyleClass().setAll("label", "estado-badge", estilo);
        return badge;
    }

    /** "CAE 70123456789012 (vence el 02/12/2026)", o null si no tiene CAE. */
    public static String cae(ComprobanteArcaDto comprobante) {

        if (comprobante == null || comprobante.getCae() == null) {
            return null;
        }

        return "CAE " + comprobante.getCae() + (comprobante.getVencimientoCae() == null ? ""
                : " (vence el " + comprobante.getVencimientoCae().format(FORMATO_FECHA) + ")");
    }

    /**
     * Resume el resultado de pedir el CAE de los comprobantes de una venta.
     *
     * @param comprobantes los que devolvió la autorización (factura y, si se anuló, nota de crédito)
     */
    public static Resultado resultado(List<ComprobanteArcaDto> comprobantes) {

        List<ComprobanteArcaDto> relevantes = comprobantes.stream()
                .filter(c -> c.getEstado() != EstadoComprobanteArca.CANCELADO)
                .toList();

        if (relevantes.isEmpty()) {
            return new Resultado(Dialogs.Tipo.INFORMACION, "Sin comprobantes para autorizar",
                    "La venta no tiene comprobantes electrónicos pendientes.", true);
        }

        StringBuilder mensaje = new StringBuilder();
        boolean rechazado = false;
        boolean sinRespuesta = false;

        for (ComprobanteArcaDto c : relevantes) {

            if (!mensaje.isEmpty()) {
                mensaje.append("\n\n");
            }

            mensaje.append(c.getComprobante());

            switch (c.getEstado()) {
                case AUTORIZADO -> mensaje.append(": autorizado por ARCA.\n").append(cae(c));
                case RECHAZADO -> {
                    rechazado = true;
                    mensaje.append(": ARCA lo rechazó.\nMotivo: ").append(Objects.toString(c.getObservaciones(), "-"));
                }
                default -> {
                    sinRespuesta = true;
                    mensaje.append(": quedó pendiente de CAE.")
                            .append(c.getObservaciones() == null ? "" : "\n" + c.getObservaciones());
                }
            }
        }

        if (rechazado) {
            mensaje.append("\n\nCorrija el motivo (por ejemplo, los datos del cliente) y reintente desde "
                    + "\"Comprobantes pendientes de CAE\".");
            return new Resultado(Dialogs.Tipo.ADVERTENCIA, "ARCA rechazó el comprobante", mensaje.toString(), false);
        }

        if (sinRespuesta) {
            mensaje.append("\n\nLa venta quedó registrada. Se puede reintentar desde \"Comprobantes pendientes "
                    + "de CAE\"; antes de reenviarlo se verifica en ARCA para no duplicarlo.");
            return new Resultado(Dialogs.Tipo.ADVERTENCIA, "Comprobante pendiente de CAE", mensaje.toString(), false);
        }

        return new Resultado(Dialogs.Tipo.EXITO, "Comprobante autorizado", mensaje.toString(), true);
    }

    /** Mensaje cuando no se pudo ni enviar el pedido (sin conexión, configuración, ...). */
    public static String noEnviado(String motivo) {
        return "No se pudo pedir el CAE: " + motivo + "\n\nLa venta quedó registrada y el comprobante, pendiente "
                + "de CAE. Se puede reintentar desde \"Comprobantes pendientes de CAE\".";
    }

}
