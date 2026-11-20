package org.gestionComercio.controller.venta;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.service.VentaService;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.util.dialog.Dialogs;

import java.util.Objects;
import java.util.Optional;

/**
 * Confirmación de la anulación de una venta: muestra sus datos, avisa qué
 * pasa con el stock y la caja, y exige el motivo. El botón de anular se
 * habilita sólo con un motivo escrito.
 *
 * <p>Si el servicio rechaza la anulación, el mensaje se muestra en la misma
 * ventana, sin cerrarla, para no perder el motivo escrito.
 */
class AnularVentaDialog {

    private final VentaService ventaService;
    private final VentaDto venta;

    private final TextArea txtMotivo = new TextArea();
    private final Label lblError = new Label();

    private VentaDto anulada;

    AnularVentaDialog(VentaService ventaService, VentaDto venta) {
        this.ventaService = ventaService;
        this.venta = venta;
    }

    /** @return la venta anulada, o vacío si se canceló */
    Optional<VentaDto> mostrar() {

        ButtonType anular = new ButtonType("Anular venta", ButtonData.OK_DONE);

        Alert alert = Dialogs.crear(Dialogs.Tipo.PELIGRO, "Anular venta " + venta.getComprobante(),
                cuerpo(), Dialogs.CANCELAR, anular);

        Button botonAnular = (Button) alert.getDialogPane().lookupButton(anular);
        Button botonCancelar = (Button) alert.getDialogPane().lookupButton(Dialogs.CANCELAR);

        // Acción destructiva: botón rojo y Enter no confirma por accidente.
        botonAnular.getStyleClass().removeAll("btn-primary", "btn-secondary");
        botonAnular.getStyleClass().add("btn-danger");
        botonAnular.setDefaultButton(false);
        botonCancelar.setDefaultButton(true);

        botonAnular.setDisable(true);
        txtMotivo.textProperty().addListener((obs, anterior, texto) -> {
            lblError.setText("");
            botonAnular.setDisable(texto == null || texto.isBlank());
        });

        botonAnular.addEventFilter(ActionEvent.ACTION, event -> {
            if (!confirmar()) {
                event.consume();
            }
        });

        Platform.runLater(txtMotivo::requestFocus);

        Optional<ButtonType> respuesta = alert.showAndWait();

        return respuesta.isPresent() && respuesta.get() == anular
                ? Optional.ofNullable(anulada)
                : Optional.empty();
    }

    private VBox cuerpo() {

        GridPane datos = new GridPane();
        datos.setHgap(12);
        datos.setVgap(4);

        ColumnConstraints etiquetas = new ColumnConstraints();
        etiquetas.setHalignment(HPos.RIGHT);
        datos.getColumnConstraints().setAll(etiquetas, new ColumnConstraints());

        Label total = new Label(VentaFormato.importe(venta.getTotal()));
        total.setStyle("-fx-font-weight: bold;");

        datos.addRow(0, new Label("Fecha:"), new Label(DateUtils.format(venta.getFecha())));
        datos.addRow(1, new Label("Cliente:"), new Label(Objects.toString(venta.getClienteNombre(), "")));
        datos.addRow(2, new Label("Forma de pago:"), new Label(Objects.toString(venta.getFormaPago(), "")));
        datos.addRow(3, new Label("Total:"), total);

        Label efectos = new Label(VentaFormato.efectosAnulacion(venta.getFormaPago(), venta.getTotal())
                + " La venta seguirá en el listado, marcada como anulada. Esta acción no se puede deshacer.");
        efectos.setWrapText(true);
        efectos.setMaxWidth(380);

        txtMotivo.setPromptText("Ej.: error en la carga, el cliente devolvió la mercadería");
        txtMotivo.setWrapText(true);
        txtMotivo.setPrefRowCount(3);
        txtMotivo.setPrefColumnCount(28);
        txtMotivo.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().length() <= VentaFormato.MOTIVO_MAX ? cambio : null));

        lblError.getStyleClass().add("field-error-label");
        lblError.setWrapText(true);
        lblError.setMaxWidth(380);

        return new VBox(10, datos, efectos, new Label("Motivo de la anulación:"), txtMotivo, lblError);
    }

    /** @return true si se anuló y la ventana se puede cerrar. */
    private boolean confirmar() {

        try {

            anulada = ventaService.anular(venta.getId(), txtMotivo.getText());
            return true;

        } catch (ValidationException ex) {

            lblError.setText(ex.getValidationResult().getMessage());
            return false;

        } catch (BusinessException ex) {

            lblError.setText(ex.getMessage());
            return false;

        } catch (Exception ex) {

            lblError.setText("No se pudo anular la venta. Si el problema persiste, comuníquese con soporte.");
            return false;
        }
    }
}
