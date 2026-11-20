package org.gestionComercio.controller.venta;

import javafx.scene.control.Label;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.NumberUtils;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Formato y validaciones que comparten las pantallas de ventas. Las funciones
 * de texto no dependen del toolkit de JavaFX, así que se pueden probar sin
 * abrir ventanas.
 */
final class VentaFormato {

    /** Mayor importe que acepta el servicio (columna precision 12, scale 2). */
    static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("9999999999.99");

    private VentaFormato() {
    }

    static String importe(BigDecimal importe) {
        return CurrencyUtils.format(importe);
    }

    /** "N° 000123". */
    static String numero(Long id) {
        return id == null ? "" : "N° " + String.format("%06d", id);
    }

    /** "Nombre Apellido (login)", o sólo el login si no hay nombre. */
    static String usuario(String nombreCompleto, String login) {

        String nombre = nombreCompleto == null ? "" : nombreCompleto.trim();

        if (nombre.isEmpty()) {
            return Objects.toString(login, "");
        }

        return login == null ? nombre : nombre + " (" + login + ")";
    }

    static String estiloEstado(EstadoVenta estado) {

        if (estado == null) {
            return "estado-inactivo";
        }

        return switch (estado) {
            case PAGADA -> "estado-activo";
            case PENDIENTE -> "movimiento-ajuste";
            case CANCELADA -> "stock-bajo";
        };
    }

    static Label badgeEstado(EstadoVenta estado) {
        Label badge = new Label(estado == null ? "" : estado.getDescripcion());
        badge.getStyleClass().setAll("label", "estado-badge", estiloEstado(estado));
        return badge;
    }

    /**
     * Interpreta un importe opcional escrito por el usuario (descuento, paga con).
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
