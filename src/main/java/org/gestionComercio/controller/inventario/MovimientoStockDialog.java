package org.gestionComercio.controller.inventario;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
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
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.entity.MovimientoInventario;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.service.InventarioService;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.NumberUtils;
import org.gestionComercio.util.dialog.Dialogs;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Ventana para registrar a mano una entrada o una salida de mercadería de un
 * producto: el usuario indica la cantidad y el motivo, y ve cómo quedará el
 * stock. Al aceptar llama a {@link InventarioService#registrarEntrada} o
 * {@link InventarioService#registrarSalida}.
 *
 * <p>En las salidas avisa antes de registrar si no hay stock suficiente (salvo
 * que el producto permita stock negativo). Si el servicio rechaza la operación
 * (por ejemplo, porque otro usuario cambió el stock mientras tanto) el mensaje
 * se muestra en la misma ventana, sin cerrarla, para no perder lo cargado.
 */
public class MovimientoStockDialog {

    public enum Operacion {

        ENTRADA("Registrar entrada", "Ej.: mercadería recibida, devolución de cliente"),
        SALIDA("Registrar salida", "Ej.: consumo interno, devolución a proveedor");

        private final String titulo;
        private final String ejemploMotivo;

        Operacion(String titulo, String ejemploMotivo) {
            this.titulo = titulo;
            this.ejemploMotivo = ejemploMotivo;
        }

        public String getTitulo() {
            return titulo;
        }

        /** Variación que aplica la operación al stock para la cantidad indicada. */
        BigDecimal variacion(BigDecimal cantidad) {
            return this == ENTRADA ? cantidad : cantidad.negate();
        }
    }

    private final InventarioService inventarioService;
    private final StockProductoDto producto;
    private final Operacion operacion;

    private final TextField txtCantidad = new TextField();
    private final Label lblStockResultante = new Label();
    private final TextField txtMotivo = new TextField();
    private final Label lblError = new Label();

    private MovimientoInventarioDto registrado;

    public MovimientoStockDialog(InventarioService inventarioService, StockProductoDto producto, Operacion operacion) {
        this.inventarioService = inventarioService;
        this.producto = producto;
        this.operacion = operacion;
    }

    /**
     * Muestra la ventana y espera a que se cierre.
     *
     * @return el movimiento registrado, o vacío si se canceló
     */
    public Optional<MovimientoInventarioDto> mostrar() {

        ButtonType confirmar = new ButtonType(operacion.getTitulo(), ButtonData.OK_DONE);

        Alert alert = Dialogs.crear(Dialogs.Tipo.CONFIRMACION, operacion.getTitulo(),
                formulario(), Dialogs.CANCELAR, confirmar);

        Button botonConfirmar = (Button) alert.getDialogPane().lookupButton(confirmar);

        // El botón se habilita recién cuando hay una cantidad válida y un motivo.
        Runnable actualizar = () -> botonConfirmar.setDisable(!actualizarEstado());
        txtCantidad.textProperty().addListener((obs, o, n) -> actualizar.run());
        txtMotivo.textProperty().addListener((obs, o, n) -> actualizar.run());
        actualizar.run();

        botonConfirmar.addEventFilter(ActionEvent.ACTION, event -> {
            if (!registrar()) {
                event.consume();
            }
        });

        Platform.runLater(txtCantidad::requestFocus);

        Optional<ButtonType> resultado = alert.showAndWait();

        return resultado.isPresent() && resultado.get() == confirmar
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

        Label lblStockActual = new Label(conUnidad(producto.stock()));

        txtCantidad.setPromptText(esFraccionable() ? "Ej.: 5 o 2,5" : "Ej.: 5");
        txtCantidad.setMaxWidth(Double.MAX_VALUE);

        txtMotivo.setPromptText(operacion.ejemploMotivo);
        txtMotivo.setMaxWidth(Double.MAX_VALUE);
        txtMotivo.setPrefColumnCount(28);
        txtMotivo.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().length() <= MovimientoInventario.MOTIVO_MAX ? cambio : null));

        lblError.getStyleClass().add("field-error-label");
        lblError.setWrapText(true);
        lblError.setMaxWidth(380);

        int fila = 0;
        grid.addRow(fila++, new Label("Producto:"), lblProducto);
        grid.addRow(fila++, new Label("Stock actual:"), lblStockActual);
        grid.addRow(fila++, new Label("Cantidad:"), txtCantidad);
        grid.addRow(fila++, new Label("Stock resultante:"), lblStockResultante);
        grid.addRow(fila, new Label("Motivo:"), txtMotivo);

        return new VBox(10, grid, lblError);
    }

    /**
     * Recalcula el stock resultante y los mensajes a medida que se completa el
     * formulario.
     *
     * @return true si se puede registrar la operación
     */
    private boolean actualizarEstado() {

        lblError.setText("");

        BigDecimal cantidad;

        try {
            cantidad = NumberUtils.parseDecimal(txtCantidad.getText());
        } catch (NumberFormatException ex) {
            mostrarStockResultante(null);
            lblError.setText("Ingrese un número válido para la cantidad.");
            return false;
        }

        if (cantidad == null) {
            mostrarStockResultante(null);
            return false;
        }

        String errorCantidad = validarCantidad(producto, cantidad);

        if (errorCantidad != null) {
            mostrarStockResultante(null);
            lblError.setText(errorCantidad);
            return false;
        }

        mostrarStockResultante(calcularStockResultante(producto, operacion, cantidad));

        String errorStock = validarStockResultante(producto, operacion, cantidad);

        if (errorStock != null) {
            lblError.setText(errorStock);
            return false;
        }

        return !txtMotivo.getText().isBlank();
    }

    /**
     * Valida la cantidad en sí, sin mirar el stock.
     *
     * @return el mensaje de error, o null si la cantidad es válida
     */
    static String validarCantidad(StockProductoDto producto, BigDecimal cantidad) {

        if (cantidad == null) {
            return "Debe indicar la cantidad.";
        }

        if (cantidad.signum() <= 0) {
            return "La cantidad debe ser mayor que cero.";
        }

        if (!CantidadUtils.escalaValida(cantidad)) {
            return "La cantidad admite como máximo " + CantidadUtils.ESCALA + " decimales.";
        }

        if (!esFraccionable(producto) && !CantidadUtils.esEntera(cantidad)) {
            return "Este producto se mide en unidades enteras.";
        }

        if (!CantidadUtils.dentroDelMaximo(cantidad)) {
            return "La cantidad supera el máximo admitido.";
        }

        return null;
    }

    /**
     * Valida el stock que quedaría tras la operación, con una cantidad ya
     * validada por {@link #validarCantidad}: que no supere el máximo y que una
     * salida no deje stock negativo si el producto no lo permite.
     *
     * @return el mensaje de error, o null si la operación es posible
     */
    static String validarStockResultante(StockProductoDto producto, Operacion operacion, BigDecimal cantidad) {

        BigDecimal resultante = calcularStockResultante(producto, operacion, cantidad);

        if (!CantidadUtils.dentroDelMaximo(resultante)) {
            return "El stock resultante supera el máximo admitido.";
        }

        if (resultante.signum() >= 0 || producto.admiteStockNegativo()) {
            return null;
        }

        // Una entrada sólo puede quedar en negativo si el stock ya lo estaba
        // (de cuando el producto lo permitía); el servicio la rechaza igual.
        if (operacion == Operacion.ENTRADA) {
            return "La entrada dejaría el stock en " + CantidadUtils.formatear(resultante)
                    + " y el producto no permite stock negativo.";
        }

        return "Stock insuficiente: disponible " + conUnidad(producto, producto.stock())
                + ", solicitado " + CantidadUtils.formatear(cantidad) + ".";
    }

    /** Stock actual más la entrada, o menos la salida. */
    static BigDecimal calcularStockResultante(StockProductoDto producto, Operacion operacion, BigDecimal cantidad) {
        return CantidadUtils.normalizar(producto.stock())
                .add(operacion.variacion(CantidadUtils.normalizar(cantidad)));
    }

    private void mostrarStockResultante(BigDecimal resultante) {

        if (resultante == null) {
            lblStockResultante.setText("—");
            lblStockResultante.setStyle("");
            return;
        }

        lblStockResultante.setText(conUnidad(resultante));
        lblStockResultante.setStyle(resultante.signum() < 0
                ? "-fx-font-weight: bold; -fx-text-fill: #c62828;"
                : "-fx-font-weight: bold;");
    }

    // ------------------------------------------------------------------
    // Registro
    // ------------------------------------------------------------------

    /** @return true si la operación quedó registrada y la ventana se puede cerrar. */
    private boolean registrar() {

        try {

            registrado = ejecutar(inventarioService, producto, operacion,
                    NumberUtils.parseDecimal(txtCantidad.getText()), txtMotivo.getText());

            return true;

        } catch (BusinessException ex) {

            lblError.setText(ex.getMessage());
            return false;

        } catch (Exception ex) {

            lblError.setText("No se pudo registrar la " + operacion.name().toLowerCase()
                    + ". Si el problema persiste, comuníquese con soporte.");
            return false;
        }
    }

    /** Registra la operación en el servicio, con el motivo sin espacios sobrantes. */
    static MovimientoInventarioDto ejecutar(
            InventarioService inventarioService,
            StockProductoDto producto,
            Operacion operacion,
            BigDecimal cantidad,
            String motivo) {

        String motivoLimpio = motivo == null ? null : motivo.trim();

        return operacion == Operacion.ENTRADA
                ? inventarioService.registrarEntrada(producto.productoId(), cantidad, motivoLimpio)
                : inventarioService.registrarSalida(producto.productoId(), cantidad, motivoLimpio);
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private boolean esFraccionable() {
        return esFraccionable(producto);
    }

    private static boolean esFraccionable(StockProductoDto producto) {
        UnidadMedida unidad = producto.unidadMedida();
        return unidad != null && unidad.isFraccionable();
    }

    private String conUnidad(BigDecimal cantidad) {
        return conUnidad(producto, cantidad);
    }

    private static String conUnidad(StockProductoDto producto, BigDecimal cantidad) {

        UnidadMedida unidad = producto.unidadMedida();

        return CantidadUtils.formatear(cantidad)
                + (unidad == null ? "" : " (" + unidad.getDescripcion() + ")");
    }
}
