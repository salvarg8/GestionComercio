package org.gestionComercio.controller.caja;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.StringConverter;
import org.gestionComercio.dto.caja.MovimientoCajaDto;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.DateUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Formato y validaciones que comparten las pantallas y ventanas de caja.
 * Las funciones de texto no dependen del toolkit de JavaFX, así que se pueden
 * probar sin abrir ventanas.
 */
final class CajaFormato {

    /** Mayor importe que acepta el servicio (columna precision 12, scale 2). */
    static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("9999999999.99");

    /** Largo máximo de la descripción de un movimiento y de la observación de cierre. */
    static final int TEXTO_MAX = 255;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private CajaFormato() {
    }

    static String importe(BigDecimal importe) {
        return CurrencyUtils.format(importe);
    }

    /** Ingresos con "+", egresos con "−" y la apertura sin signo. */
    static String importeConSigno(TipoMovimientoCaja tipo, BigDecimal importe) {

        if (importe == null) {
            return "";
        }

        String texto = importe(importe);

        if (tipo == null || tipo.getSigno() == 0) {
            return texto;
        }

        return (tipo.esIngreso() ? "+ " : "− ") + texto;
    }

    static String estilo(TipoMovimientoCaja tipo) {

        if (tipo.esIngreso()) {
            return "estado-activo";
        }

        if (tipo.esEgreso()) {
            return "stock-bajo";
        }

        return tipo == TipoMovimientoCaja.APERTURA ? "movimiento-ajuste" : "estado-inactivo";
    }

    /** "Sin diferencia", "Sobrante $ x" o "Faltante $ x". */
    static String textoDiferencia(BigDecimal diferencia) {

        if (diferencia == null) {
            return "";
        }

        if (diferencia.signum() == 0) {
            return "Sin diferencia";
        }

        return (diferencia.signum() > 0 ? "Sobrante " : "Faltante ") + importe(diferencia.abs());
    }

    static String estiloDiferencia(BigDecimal diferencia) {

        if (diferencia == null || diferencia.signum() == 0) {
            return "estado-activo";
        }

        return diferencia.signum() > 0 ? "movimiento-ajuste" : "stock-bajo";
    }

    /** "Nombre Apellido (login)", o sólo el login si no hay nombre. */
    static String usuario(String nombreCompleto, String login) {

        String nombre = nombreCompleto == null ? "" : nombreCompleto.trim();

        if (nombre.isEmpty()) {
            return Objects.toString(login, "");
        }

        return login == null ? nombre : nombre + " (" + login + ")";
    }

    /**
     * Valida un importe ya interpretado del texto ingresado.
     *
     * @param campo        nombre del campo para el mensaje, por ejemplo "El importe"
     * @param permitirCero true para saldos (cero o más), false para movimientos (más que cero)
     * @return el mensaje de error, o null si el importe es válido
     */
    static String errorImporte(BigDecimal importe, String campo, boolean permitirCero) {

        if (importe == null) {
            return campo + " es obligatorio.";
        }

        if (permitirCero ? importe.signum() < 0 : importe.signum() <= 0) {
            return campo + (permitirCero ? " no puede ser negativo." : " debe ser mayor que cero.");
        }

        if (importe.stripTrailingZeros().scale() > 2) {
            return campo + " admite hasta dos decimales.";
        }

        if (importe.compareTo(IMPORTE_MAXIMO) > 0) {
            return campo + " supera el máximo permitido.";
        }

        return null;
    }

    // ------------------------------------------------------------------
    // Controles
    // ------------------------------------------------------------------

    /** Columna de tipo de movimiento con un distintivo de color. */
    static void configurarColumnaTipo(TableColumn<MovimientoCajaDto, TipoMovimientoCaja> columna) {

        columna.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTipo()));

        columna.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(TipoMovimientoCaja tipo, boolean empty) {

                super.updateItem(tipo, empty);

                if (empty || tipo == null) {
                    setGraphic(null);
                    return;
                }

                badge.setText(tipo.getDescripcion());
                badge.getStyleClass().setAll("label", "estado-badge", estilo(tipo));

                setGraphic(badge);
            }
        });
    }

    /** Configura las columnas de una tabla de movimientos de caja. */
    static void configurarColumnasMovimientos(
            TableColumn<MovimientoCajaDto, String> colFecha,
            TableColumn<MovimientoCajaDto, TipoMovimientoCaja> colTipo,
            TableColumn<MovimientoCajaDto, String> colDescripcion,
            TableColumn<MovimientoCajaDto, String> colImporte,
            TableColumn<MovimientoCajaDto, String> colUsuario) {

        colFecha.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.format(c.getValue().getFecha())));

        configurarColumnaTipo(colTipo);

        colDescripcion.setCellValueFactory(c -> new SimpleStringProperty(
                Objects.toString(c.getValue().getDescripcion(), "")));

        colImporte.setCellValueFactory(c -> new SimpleStringProperty(
                importeConSigno(c.getValue().getTipo(), c.getValue().getImporte())));

        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(
                usuario(c.getValue().getUsuarioNombreCompleto(), c.getValue().getUsuarioNombre())));
    }

    /** Formato dd/MM/yyyy; un texto que no es una fecha válida deja el campo vacío. */
    static void configurarFecha(DatePicker datePicker) {

        datePicker.setPromptText("dd/mm/aaaa");

        datePicker.setConverter(new StringConverter<>() {

            @Override
            public String toString(LocalDate fecha) {
                return fecha == null ? "" : fecha.format(FORMATO_FECHA);
            }

            @Override
            public LocalDate fromString(String texto) {

                if (texto == null || texto.isBlank()) {
                    return null;
                }

                try {
                    return LocalDate.parse(texto.trim(), FORMATO_FECHA);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
    }
}
