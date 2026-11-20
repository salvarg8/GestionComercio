package org.gestionComercio.controller.compra;

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
import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.service.CompraService;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.util.dialog.Dialogs;

import java.util.Objects;
import java.util.Optional;

/**
 * Confirmación de la anulación de una compra: muestra sus datos, avisa qué
 * pasa con el stock y la caja, y exige el motivo. El botón de anular se
 * habilita sólo con un motivo escrito.
 *
 * <p>Si el servicio rechaza la anulación (por ejemplo, porque descontar el
 * stock lo dejaría negativo), el mensaje se muestra en la misma ventana, sin
 * cerrarla, para no perder el motivo escrito.
 */
class AnularCompraDialog {

    private final CompraService compraService;
    private final CompraDto compra;

    private final TextArea txtMotivo = new TextArea();
    private final Label lblError = new Label();

    private CompraDto anulada;

    AnularCompraDialog(CompraService compraService, CompraDto compra) {
        this.compraService = compraService;
        this.compra = compra;
    }

    /** @return la compra anulada, o vacío si se canceló */
    Optional<CompraDto> mostrar() {

        ButtonType anular = new ButtonType("Anular compra", ButtonData.OK_DONE);

        Alert alert = Dialogs.crear(Dialogs.Tipo.PELIGRO, "Anular " + CompraFormato.titulo(compra).toLowerCase(),
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

        Label total = new Label(CompraFormato.importe(compra.getTotal()));
        total.setStyle("-fx-font-weight: bold;");

        datos.addRow(0, new Label("Proveedor:"), new Label(Objects.toString(compra.getProveedorNombre(), "")));
        datos.addRow(1, new Label("Factura:"), new Label(CompraFormato.factura(compra.getNumeroFactura())));
        datos.addRow(2, new Label("Fecha:"), new Label(DateUtils.format(compra.getFecha())));
        datos.addRow(3, new Label("Forma de pago:"), new Label(Objects.toString(compra.getFormaPago(), "")));
        datos.addRow(4, new Label("Total:"), total);

        Label efectos = new Label(CompraFormato.efectosAnulacion(
                compra.getEstadoCompra(), compra.getCajaId() != null, compra.getTotal())
                + " La compra seguirá en el listado, marcada como anulada. Esta acción no se puede deshacer.");
        efectos.setWrapText(true);
        efectos.setMaxWidth(380);

        txtMotivo.setPromptText("Ej.: error en la carga, mercadería devuelta al proveedor");
        txtMotivo.setWrapText(true);
        txtMotivo.setPrefRowCount(3);
        txtMotivo.setPrefColumnCount(28);
        txtMotivo.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().length() <= CompraFormato.MOTIVO_MAX ? cambio : null));

        lblError.getStyleClass().add("field-error-label");
        lblError.setWrapText(true);
        lblError.setMaxWidth(380);

        return new VBox(10, datos, efectos, new Label("Motivo de la anulación:"), txtMotivo, lblError);
    }

    /** @return true si se anuló y la ventana se puede cerrar. */
    private boolean confirmar() {

        try {

            anulada = compraService.anular(compra.getId(), txtMotivo.getText());
            return true;

        } catch (ValidationException ex) {

            lblError.setText(ex.getValidationResult().getMessage());
            return false;

        } catch (BusinessException ex) {

            lblError.setText(ex.getMessage());
            return false;

        } catch (Exception ex) {

            lblError.setText("No se pudo anular la compra. Si el problema persiste, comuníquese con soporte.");
            return false;
        }
    }
}
