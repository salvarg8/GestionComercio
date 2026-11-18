package org.gestionComercio.controller.inventario;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.entity.MovimientoInventario;
import org.gestionComercio.enums.MotivoAjusteInventario;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.service.InventarioService;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.NumberUtils;
import org.gestionComercio.util.dialog.Dialogs;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Ventana para corregir el stock de un producto a partir de un recuento: el
 * usuario indica el stock real, ve la diferencia con el del sistema y elige
 * el motivo. Al aceptar registra un movimiento de tipo AJUSTE con
 * {@link InventarioService#ajustarStock}.
 *
 * <p>Si el servicio rechaza el ajuste (por ejemplo, porque otro usuario cambió
 * el stock mientras tanto) el mensaje se muestra en la misma ventana, sin
 * cerrarla, para no perder lo que se cargó.
 */
public class AjusteStockDialog {

    private final InventarioService inventarioService;
    private final StockProductoDto producto;

    private final TextField txtStockReal = new TextField();
    private final Label lblDiferencia = new Label();
    private final ComboBox<MotivoAjusteInventario> cmbCausa = new ComboBox<>();
    private final TextField txtDetalle = new TextField();
    private final Label lblError = new Label();

    private MovimientoInventarioDto registrado;

    public AjusteStockDialog(InventarioService inventarioService, StockProductoDto producto) {
        this.inventarioService = inventarioService;
        this.producto = producto;
    }

    /**
     * Muestra la ventana y espera a que se cierre.
     *
     * @return el movimiento registrado, o vacío si se canceló
     */
    public Optional<MovimientoInventarioDto> mostrar() {

        ButtonType ajustar = new ButtonType("Ajustar stock", ButtonData.OK_DONE);

        Alert alert = Dialogs.crear(Dialogs.Tipo.CONFIRMACION, "Ajustar stock",
                formulario(), Dialogs.CANCELAR, ajustar);

        Button botonAjustar = (Button) alert.getDialogPane().lookupButton(ajustar);

        // El botón se habilita recién cuando hay un stock válido, distinto del
        // actual, y un motivo completo.
        Runnable actualizar = () -> botonAjustar.setDisable(!actualizarEstado());
        txtStockReal.textProperty().addListener((obs, o, n) -> actualizar.run());
        cmbCausa.valueProperty().addListener((obs, o, n) -> actualizar.run());
        txtDetalle.textProperty().addListener((obs, o, n) -> actualizar.run());
        actualizar.run();

        botonAjustar.addEventFilter(ActionEvent.ACTION, event -> {
            if (!registrar()) {
                event.consume();
            }
        });

        Platform.runLater(txtStockReal::requestFocus);

        Optional<ButtonType> resultado = alert.showAndWait();

        return resultado.isPresent() && resultado.get() == ajustar
                ? Optional.ofNullable(registrado)
                : Optional.empty();
    }

    // ------------------------------------------------------------------
    // Formulario
    // ------------------------------------------------------------------

    private VBox formulario() {

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        ColumnConstraints etiquetas = new ColumnConstraints();
        etiquetas.setHalignment(HPos.RIGHT);
        ColumnConstraints campos = new ColumnConstraints();
        campos.setHgrow(Priority.ALWAYS);
        campos.setFillWidth(true);
        grid.getColumnConstraints().setAll(etiquetas, campos);

        Label lblProducto = new Label(producto.codigo() == null || producto.codigo().isBlank()
                ? producto.nombre()
                : producto.codigo() + " — " + producto.nombre());
        lblProducto.setWrapText(true);
        lblProducto.setStyle("-fx-font-weight: bold;");

        Label lblStockSistema = new Label(conUnidad(producto.stock()));

        txtStockReal.setPromptText(esFraccionable() ? "Ej.: 12 o 2,5" : "Ej.: 12");
        txtStockReal.setMaxWidth(Double.MAX_VALUE);

        cmbCausa.getItems().setAll(MotivoAjusteInventario.values());
        cmbCausa.setPromptText("Seleccione el motivo");
        cmbCausa.setMaxWidth(Double.MAX_VALUE);

        txtDetalle.setPromptText("Detalle opcional");
        txtDetalle.setMaxWidth(Double.MAX_VALUE);
        txtDetalle.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().length() <= MovimientoInventario.MOTIVO_MAX ? cambio : null));

        lblError.getStyleClass().add("field-error-label");
        lblError.setWrapText(true);
        lblError.setMaxWidth(380);

        int fila = 0;
        grid.addRow(fila++, new Label("Producto:"), lblProducto);
        grid.addRow(fila++, new Label("Stock en sistema:"), lblStockSistema);
        grid.addRow(fila++, new Label("Stock real:"), txtStockReal);
        grid.addRow(fila++, new Label("Diferencia:"), lblDiferencia);
        grid.addRow(fila++, new Label("Motivo:"), cmbCausa);
        grid.addRow(fila, new Label("Detalle:"), txtDetalle);

        return new VBox(10, grid, lblError);
    }

    /**
     * Recalcula la diferencia y los mensajes a medida que se completa el
     * formulario.
     *
     * @return true si se puede registrar el ajuste
     */
    private boolean actualizarEstado() {

        MotivoAjusteInventario causa = cmbCausa.getValue();
        boolean requiereDetalle = causa != null && causa.requiereDetalle();
        txtDetalle.setPromptText(requiereDetalle ? "Obligatorio: describa el motivo" : "Detalle opcional");

        lblError.setText("");

        BigDecimal stockReal;

        try {
            stockReal = NumberUtils.parseDecimal(txtStockReal.getText());
        } catch (NumberFormatException ex) {
            mostrarDiferencia(null);
            lblError.setText("Ingrese un número válido para el stock real.");
            return false;
        }

        if (stockReal == null) {
            mostrarDiferencia(null);
            return false;
        }

        String errorStock = validarStockReal(stockReal);

        if (errorStock != null) {
            mostrarDiferencia(null);
            lblError.setText(errorStock);
            return false;
        }

        BigDecimal diferencia = calcularDiferencia(stockReal);
        mostrarDiferencia(diferencia);

        if (diferencia.signum() == 0) {
            return false;
        }

        if (causa == null) {
            return false;
        }

        return !requiereDetalle || !txtDetalle.getText().isBlank();
    }

    private String validarStockReal(BigDecimal stockReal) {

        if (stockReal.signum() < 0) {
            return "El stock real no puede ser negativo.";
        }

        if (!CantidadUtils.escalaValida(stockReal)) {
            return "El stock admite como máximo " + CantidadUtils.ESCALA + " decimales.";
        }

        if (!esFraccionable() && !CantidadUtils.esEntera(stockReal)) {
            return "Este producto se mide en unidades enteras.";
        }

        if (!CantidadUtils.dentroDelMaximo(stockReal)) {
            return "El stock supera el máximo admitido.";
        }

        return null;
    }

    /** Stock real menos stock del sistema: positiva si sobra mercadería, negativa si falta. */
    BigDecimal calcularDiferencia(BigDecimal stockReal) {
        return CantidadUtils.normalizar(stockReal).subtract(CantidadUtils.normalizar(producto.stock()));
    }

    private void mostrarDiferencia(BigDecimal diferencia) {

        if (diferencia == null) {
            lblDiferencia.setText("—");
            lblDiferencia.setStyle("");
            return;
        }

        if (diferencia.signum() == 0) {
            lblDiferencia.setText("Sin diferencia: el stock coincide con el del sistema.");
            lblDiferencia.setStyle("");
            return;
        }

        String signo = diferencia.signum() > 0 ? "+" : "";
        lblDiferencia.setText(signo + conUnidad(diferencia)
                + (diferencia.signum() > 0 ? "  (sobrante)" : "  (faltante)"));
        lblDiferencia.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                + (diferencia.signum() > 0 ? "#2e7d32;" : "#c62828;"));
    }

    // ------------------------------------------------------------------
    // Registro
    // ------------------------------------------------------------------

    /** @return true si el ajuste quedó registrado y la ventana se puede cerrar. */
    private boolean registrar() {

        try {

            registrado = inventarioService.ajustarStock(
                    producto.productoId(),
                    NumberUtils.parseDecimal(txtStockReal.getText()),
                    cmbCausa.getValue(),
                    txtDetalle.getText() == null ? null : txtDetalle.getText().trim());

            return true;

        } catch (BusinessException ex) {

            lblError.setText(ex.getMessage());
            return false;

        } catch (Exception ex) {

            lblError.setText("No se pudo registrar el ajuste. Si el problema persiste, comuníquese con soporte.");
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private boolean esFraccionable() {
        UnidadMedida unidad = producto.unidadMedida();
        return unidad != null && unidad.isFraccionable();
    }

    private String conUnidad(BigDecimal cantidad) {

        UnidadMedida unidad = producto.unidadMedida();

        return CantidadUtils.formatear(cantidad)
                + (unidad == null ? "" : " (" + unidad.getDescripcion() + ")");
    }
}
