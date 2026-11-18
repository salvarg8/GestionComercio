package org.gestionComercio.controller.caja;

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.util.NumberUtils;
import org.gestionComercio.util.dialog.Dialogs;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Base de las ventanas de caja (apertura, movimiento y cierre): un formulario
 * de etiquetas y campos, un mensaje de error y un botón de confirmar que se
 * habilita sólo cuando los datos son válidos.
 *
 * <p>Si el servicio rechaza la operación, el mensaje se muestra en la misma
 * ventana, sin cerrarla, para no perder lo cargado.
 *
 * @param <R> lo que devuelve el servicio al confirmar
 */
abstract class FormularioCajaDialog<R> {

    private final GridPane grid = new GridPane();
    private final Label lblError = new Label();
    private int fila;

    private R resultado;

    /** Título de la ventana. */
    protected abstract String titulo();

    /** Texto del botón de confirmar. */
    protected abstract String textoConfirmar();

    /** Agrega las filas del formulario con {@link #fila(String, Node)}. */
    protected abstract void armarFormulario();

    /** Valores (texto de campos, selección de combos) cuyos cambios recalculan el formulario. */
    protected abstract Observable[] observados();

    /** Campo que recibe el foco al abrir. */
    protected abstract Node campoInicial();

    /**
     * Recalcula los valores derivados y los mensajes a medida que se completa
     * el formulario. Para informar un problema usa {@link #mostrarError(String)}.
     *
     * @return true si se puede confirmar
     */
    protected abstract boolean actualizarEstado();

    /** Ejecuta la operación en el servicio. */
    protected abstract R ejecutar();

    /** Permite ajustar el botón de confirmar (por ejemplo, estilo de acción peligrosa). */
    protected void configurarBotonConfirmar(Button boton) {
    }

    /**
     * Muestra la ventana y espera a que se cierre.
     *
     * @return el resultado de la operación, o vacío si se canceló
     */
    public Optional<R> mostrar() {

        ButtonType confirmar = new ButtonType(textoConfirmar(), ButtonData.OK_DONE);

        Alert alert = Dialogs.crear(Dialogs.Tipo.CONFIRMACION, titulo(), formulario(),
                Dialogs.CANCELAR, confirmar);

        Button botonConfirmar = (Button) alert.getDialogPane().lookupButton(confirmar);
        configurarBotonConfirmar(botonConfirmar);

        Runnable actualizar = () -> {
            lblError.setText("");
            botonConfirmar.setDisable(!actualizarEstado());
        };

        for (Observable observado : observados()) {
            observado.addListener(obs -> actualizar.run());
        }

        actualizar.run();

        botonConfirmar.addEventFilter(ActionEvent.ACTION, event -> {
            if (!confirmar()) {
                event.consume();
            }
        });

        Platform.runLater(campoInicial()::requestFocus);

        Optional<ButtonType> respuesta = alert.showAndWait();

        return respuesta.isPresent() && respuesta.get() == confirmar
                ? Optional.ofNullable(resultado)
                : Optional.empty();
    }

    // ------------------------------------------------------------------
    // Para las subclases
    // ------------------------------------------------------------------

    protected void fila(String etiqueta, Node campo) {
        grid.addRow(fila++, new Label(etiqueta), campo);
    }

    protected void mostrarError(String mensaje) {
        lblError.setText(mensaje);
    }

    /** Campo de importe con ancho completo. */
    protected static TextField campoImporte(TextField campo) {
        campo.setPromptText("Ej.: 1500 o 1500,50");
        campo.setMaxWidth(Double.MAX_VALUE);
        return campo;
    }

    /** Campo de texto libre limitado a {@link CajaFormato#TEXTO_MAX} caracteres. */
    protected static TextField campoTexto(TextField campo, String ejemplo) {
        campo.setPromptText(ejemplo);
        campo.setMaxWidth(Double.MAX_VALUE);
        campo.setPrefColumnCount(28);
        campo.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().length() <= CajaFormato.TEXTO_MAX ? cambio : null));
        return campo;
    }

    /**
     * Interpreta y valida el importe escrito; si no es válido muestra el error.
     *
     * @return el importe, o null si falta o no es válido
     */
    protected BigDecimal leerImporte(TextField campo, String nombre, boolean permitirCero) {

        BigDecimal importe;

        try {
            importe = NumberUtils.parseDecimal(campo.getText());
        } catch (NumberFormatException ex) {
            mostrarError("Ingrese un importe válido.");
            return null;
        }

        if (importe == null) {
            return null;
        }

        String error = CajaFormato.errorImporte(importe, nombre, permitirCero);

        if (error != null) {
            mostrarError(error);
            return null;
        }

        return importe;
    }

    /** Etiqueta en negrita para valores calculados. */
    protected static Label valor(String texto) {
        Label label = new Label(texto);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    private VBox formulario() {

        grid.setHgap(12);
        grid.setVgap(10);

        ColumnConstraints etiquetas = new ColumnConstraints();
        etiquetas.setHalignment(HPos.RIGHT);
        ColumnConstraints campos = new ColumnConstraints();
        campos.setHgrow(Priority.ALWAYS);
        campos.setFillWidth(true);
        grid.getColumnConstraints().setAll(etiquetas, campos);

        armarFormulario();

        lblError.getStyleClass().add("field-error-label");
        lblError.setWrapText(true);
        lblError.setMaxWidth(380);

        return new VBox(10, grid, lblError);
    }

    /** @return true si la operación se realizó y la ventana se puede cerrar. */
    private boolean confirmar() {

        try {

            resultado = ejecutar();
            return true;

        } catch (ValidationException ex) {

            mostrarError(ex.getValidationResult().getMessage());
            return false;

        } catch (BusinessException ex) {

            mostrarError(ex.getMessage());
            return false;

        } catch (Exception ex) {

            mostrarError("No se pudo completar la operación. Si el problema persiste, comuníquese con soporte.");
            return false;
        }
    }
}
