package org.gestionComercio.controller.compra;

import javafx.scene.control.Label;
import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.enums.EstadoCompra;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.NumberUtils;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Formato y reglas que comparten las pantallas de compras. Las funciones de
 * texto no dependen del toolkit de JavaFX, así que se pueden probar sin abrir
 * ventanas.
 */
final class CompraFormato {

    /** Mayor importe que acepta el servicio (columna precision 12, scale 2). */
    static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("9999999999.99");

    /** Largo máximo del motivo de anulación (columna varchar 255). */
    static final int MOTIVO_MAX = 255;

    /** Largo máximo del número de factura (columna varchar 50). */
    static final int NUMERO_FACTURA_MAX = 50;

    private CompraFormato() {
    }

    static String importe(BigDecimal importe) {
        return CurrencyUtils.format(importe);
    }

    /** "Compra N° 12". */
    static String titulo(CompraDto compra) {
        return "Compra N° " + compra.getId();
    }

    /** El número de factura, o "Sin factura". */
    static String factura(String numeroFactura) {
        return numeroFactura == null || numeroFactura.isBlank() ? "Sin factura" : numeroFactura;
    }

    /** "Nombre Apellido (login)", o sólo el login si no hay nombre. */
    static String usuario(String nombreCompleto, String login) {

        String nombre = nombreCompleto == null ? "" : nombreCompleto.trim();

        if (nombre.isEmpty()) {
            return Objects.toString(login, "");
        }

        return login == null ? nombre : nombre + " (" + login + ")";
    }

    static String estiloEstado(EstadoCompra estado) {

        if (estado == null) {
            return "estado-inactivo";
        }

        return switch (estado) {
            case RECIBIDA -> "estado-activo";
            case PENDIENTE -> "movimiento-ajuste";
            case CANCELADA, ANULADA -> "stock-bajo";
        };
    }

    static Label badgeEstado(EstadoCompra estado) {
        Label badge = new Label(estado == null ? "" : estado.getDescripcion());
        badge.getStyleClass().setAll("label", "estado-badge", estiloEstado(estado));
        return badge;
    }

    /** @return true si la compra todavía se puede anular (pendiente o recibida). */
    static boolean anulable(EstadoCompra estado) {
        return estado == EstadoCompra.PENDIENTE || estado == EstadoCompra.RECIBIDA;
    }

    /** @return true si la compra espera la mercadería (se puede marcar como recibida). */
    static boolean recibible(EstadoCompra estado) {
        return estado == EstadoCompra.PENDIENTE;
    }

    /** @return true si la compra cuenta en el importe comprado (no anulada ni cancelada). */
    static boolean sumaAlImporte(EstadoCompra estado) {
        return estado != EstadoCompra.ANULADA && estado != EstadoCompra.CANCELADA;
    }

    /**
     * Qué pasa al anular la compra, para avisarlo antes de confirmar.
     *
     * @param pagadaEnEfectivo true si la compra tiene caja (se pagó en efectivo)
     */
    static String efectosAnulacion(EstadoCompra estado, boolean pagadaEnEfectivo, BigDecimal total) {

        String stock = estado == EstadoCompra.RECIBIDA
                ? "Se descontará del stock lo que la compra sumó."
                : "La compra no se recibió: el stock no cambia.";

        if (pagadaEnEfectivo && total != null && total.signum() > 0) {
            return stock + " Se registrará un ingreso de " + importe(total)
                    + " en caja por el reintegro del efectivo.";
        }

        return stock + " No se registra ningún movimiento de caja.";
    }

    /**
     * Interpreta un importe escrito por el usuario (precio de compra).
     *
     * @return el importe, o null si el campo está vacío
     * @throws IllegalArgumentException con el mensaje para el usuario si no es válido
     */
    static BigDecimal leerImporte(String texto, String campo) {

        BigDecimal importe;

        try {
            importe = NumberUtils.parseDecimal(texto);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(campo + ": ingrese un importe válido.");
        }

        if (importe == null) {
            return null;
        }

        if (importe.signum() < 0) {
            throw new IllegalArgumentException(campo + " no puede ser negativo.");
        }

        if (importe.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(campo + " admite hasta dos decimales.");
        }

        if (importe.compareTo(IMPORTE_MAXIMO) > 0) {
            throw new IllegalArgumentException(campo + " supera el máximo permitido.");
        }

        return importe;
    }
}
