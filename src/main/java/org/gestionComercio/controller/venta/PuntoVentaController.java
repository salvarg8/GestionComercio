package org.gestionComercio.controller.venta;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.venta.CarritoVenta.Cobro;
import org.gestionComercio.controller.venta.CarritoVenta.Renglon;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.VentaService;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.NumberUtils;
import org.gestionComercio.util.dialog.Dialogs;
import org.gestionComercio.view.component.ComboBoxBusqueda;
import org.gestionComercio.view.component.table.RowActionsTableCell;
import org.gestionComercio.view.component.table.RowActionsTableCell.Accion;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Punto de venta: se cargan productos con el lector de código de barras o
 * buscándolos, se elige cliente (opcional) y forma de pago, y se confirma con
 * {@link VentaService#registrar}. Pensado para operar sólo con el teclado
 * (ver los atajos en {@link #atajo(KeyEvent)}).
 *
 * <p>Requiere una caja abierta del vendedor; si no la tiene, lo indica y
 * ofrece ir a la caja. Antes de confirmar vuelve a controlar el stock y, si
 * falta para algún producto, los informa todos juntos sin registrar nada.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class PuntoVentaController extends AbstractController {

    private static final PseudoClass SIN_STOCK = PseudoClass.getPseudoClass("sin-stock");

    /** Opción fija del combo de clientes: la venta queda sin cliente. */
    private static final ClienteDto CONSUMIDOR_FINAL = ClienteDto.builder().nombre(VentaDto.CONSUMIDOR_FINAL).build();

    @FXML
    private BorderPane root;

    @FXML
    private Label lblCaja;

    @FXML
    private Button btnHistorial;

    @FXML
    private VBox panelSinCaja;

    @FXML
    private Button btnIrACaja;

    @FXML
    private Label lblSinAccesoCaja;

    @FXML
    private Button btnReintentar;

    @FXML
    private BorderPane panelVenta;

    @FXML
    private TextField txtBuscar;

    @FXML
    private TextField txtCantidad;

    @FXML
    private Button btnAgregar;

    @FXML
    private TableView<Renglon> tblCarrito;

    @FXML
    private TableColumn<Renglon, String> colCodigo;

    @FXML
    private TableColumn<Renglon, String> colProducto;

    @FXML
    private TableColumn<Renglon, BigDecimal> colCantidad;

    @FXML
    private TableColumn<Renglon, String> colPrecio;

    @FXML
    private TableColumn<Renglon, String> colSubtotal;

    @FXML
    private TableColumn<Renglon, Renglon> colDisponible;

    @FXML
    private TableColumn<Renglon, Void> colAcciones;

    @FXML
    private Label lblAvisoStock;

    @FXML
    private ComboBox<TipoComprobante> cmbTipoComprobante;

    @FXML
    private ComboBox<ClienteDto> cmbCliente;

    @FXML
    private ComboBox<FormaPago> cmbFormaPago;

    @FXML
    private Label lblSubtotal;

    @FXML
    private TextField txtDescuento;

    @FXML
    private Label lblTotal;

    @FXML
    private GridPane panelEfectivo;

    @FXML
    private TextField txtPagaCon;

    @FXML
    private Label lblVuelto;

    @FXML
    private Label lblError;

    @FXML
    private Button btnConfirmar;

    @FXML
    private Button btnVaciar;

    private final VentaService ventaService;
    private final Navigator navigator;
    private final ImpresionComprobante impresionComprobante;

    private final CarritoVenta carrito = new CarritoVenta();

    @Override
    protected void initializeComponents() {

        configurarTabla();
        configurarCliente();

        cmbFormaPago.getItems().setAll(FormaPago.delPuntoDeVenta());
        cmbFormaPago.setValue(FormaPago.EFECTIVO);

        cmbTipoComprobante.getItems().setAll(TipoComprobante.delPuntoDeVenta());
        cmbTipoComprobante.setValue(TipoComprobante.TICKET);

        mostrar(btnHistorial, navigator.puedeAcceder(AppView.VENTA_LIST));

        verificarCaja();
        refrescarCarrito();
    }

    @Override
    protected void initializeEvents() {

        root.addEventFilter(KeyEvent.KEY_PRESSED, this::atajo);

        txtBuscar.setOnAction(e -> agregarDesdeBusqueda());
        btnAgregar.setOnAction(e -> agregarDesdeBusqueda());

        txtBuscar.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN && !tblCarrito.getItems().isEmpty()) {
                e.consume();
                enfocarTabla();
            }
        });

        // Enter en la cantidad agrega lo buscado, o vuelve a la búsqueda si está vacía.
        txtCantidad.setOnAction(e -> {
            if (txtBuscar.getText().isBlank()) {
                enfocarBusqueda();
            } else {
                agregarDesdeBusqueda();
            }
        });

        tblCarrito.setOnKeyPressed(this::teclaEnTabla);

        cmbFormaPago.valueProperty().addListener((obs, o, n) -> {
            mostrar(panelEfectivo, n == FormaPago.EFECTIVO);
            actualizarTotales();
        });

        txtDescuento.textProperty().addListener((obs, o, n) -> actualizarTotales());
        txtPagaCon.textProperty().addListener((obs, o, n) -> actualizarTotales());

        // Enter en "paga con" confirma la venta (pide confirmación antes de registrar).
        txtPagaCon.setOnAction(e -> confirmar());

        btnConfirmar.setOnAction(e -> confirmar());
        btnVaciar.setOnAction(e -> vaciar());

        btnHistorial.setOnAction(e -> navigator.navigate(AppView.VENTA_LIST));
        btnIrACaja.setOnAction(e -> navigator.navigate(AppView.CAJA));
        btnReintentar.setOnAction(e -> verificarCaja());
    }

    // ------------------------------------------------------------------
    // Configuración
    // ------------------------------------------------------------------

    private void configurarTabla() {

        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProducto().getCodigo()));
        colProducto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProducto().getNombre()));
        colPrecio.setCellValueFactory(c -> new SimpleStringProperty(VentaFormato.importe(c.getValue().getPrecioUnitario())));
        colSubtotal.setCellValueFactory(c -> new SimpleStringProperty(VentaFormato.importe(c.getValue().getSubtotal())));

        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidad()));
        colCantidad.setCellFactory(TextFieldTableCell.forTableColumn(new StringConverter<>() {

            @Override
            public String toString(BigDecimal cantidad) {
                return CantidadUtils.formatearEditable(cantidad);
            }

            @Override
            public BigDecimal fromString(String texto) {
                try {
                    return NumberUtils.parseDecimal(texto);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }));
        colCantidad.setOnEditCommit(e -> cambiarCantidad(e.getRowValue(), e.getNewValue()));

        colDisponible.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        colDisponible.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(Renglon renglon, boolean empty) {

                super.updateItem(renglon, empty);

                if (empty || renglon == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                String disponible = SeleccionProductoDialog.disponible(renglon.getProducto());

                if (renglon.superaStock()) {
                    badge.setText(disponible);
                    badge.getStyleClass().setAll("label", "estado-badge", "stock-bajo");
                    setText(null);
                    setGraphic(badge);
                } else {
                    setText(disponible);
                    setGraphic(null);
                }
            }
        });

        colAcciones.setCellFactory(column -> new RowActionsTableCell<>(List.of(
                new Accion<Renglon>(FontAwesomeSolid.PEN, "Cambiar cantidad (F3)", "btn-icon-edit",
                        r -> true, this::editarCantidad),
                new Accion<Renglon>(FontAwesomeSolid.TRASH, "Quitar (Supr)", "btn-icon-delete",
                        r -> true, this::quitar))));

        tblCarrito.setRowFactory(tabla -> new TableRow<>() {

            @Override
            protected void updateItem(Renglon renglon, boolean empty) {
                super.updateItem(renglon, empty);
                pseudoClassStateChanged(SIN_STOCK, !empty && renglon != null && renglon.superaStock());
            }
        });
    }

    /** Combo con búsqueda por nombre o documento; "Consumidor final" queda siempre visible. */
    private void configurarCliente() {

        List<ClienteDto> lista = new ArrayList<>();
        lista.add(CONSUMIDOR_FINAL);

        execute(() -> lista.addAll(ventaService.listarClientes()));

        ComboBoxBusqueda.configurar(cmbCliente,
                c -> c == CONSUMIDOR_FINAL
                        ? VentaDto.CONSUMIDOR_FINAL
                        : c.getNombreCompleto() + " — " + c.getDocumentoFormateado(),
                c -> c == CONSUMIDOR_FINAL
                        ? null
                        : c.getNombreCompleto() + " " + Objects.toString(c.getNumeroDocumento(), ""))
                .setAll(lista);

        cmbCliente.setValue(CONSUMIDOR_FINAL);
        cmbCliente.setVisibleRowCount(12);
        cmbCliente.setPromptText("Buscar cliente por nombre o documento...");
    }

    // ------------------------------------------------------------------
    // Caja
    // ------------------------------------------------------------------

    private void verificarCaja() {

        execute(() -> {

            boolean abierta = ventaService.tieneCajaAbierta();

            mostrar(panelVenta, abierta);
            mostrar(panelSinCaja, !abierta);

            lblCaja.setText(abierta ? "Caja abierta" : "Sin caja abierta");
            lblCaja.getStyleClass().setAll("label", "estado-badge", abierta ? "estado-activo" : "estado-inactivo");

            boolean accesoCaja = navigator.puedeAcceder(AppView.CAJA);
            mostrar(btnIrACaja, accesoCaja);
            mostrar(lblSinAccesoCaja, !accesoCaja);

            if (abierta) {
                enfocarBusqueda();
            }
        });
    }

    // ------------------------------------------------------------------
    // Carrito
    // ------------------------------------------------------------------

    /**
     * Busca lo escrito: primero un código exacto (lector de código de barras o
     * código interno); si no, por nombre o código. Con una sola coincidencia la
     * agrega directamente; con varias deja elegir.
     */
    private void agregarDesdeBusqueda() {

        String texto = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();

        if (texto.isEmpty()) {
            enfocarBusqueda();
            return;
        }

        BigDecimal cantidad;

        try {
            cantidad = Objects.requireNonNullElse(NumberUtils.parseDecimal(txtCantidad.getText()), BigDecimal.ONE);
        } catch (NumberFormatException e) {
            warning("Ingrese una cantidad válida.");
            txtCantidad.requestFocus();
            txtCantidad.selectAll();
            return;
        }

        execute(() -> {

            Optional<ProductoDto> producto = ventaService.buscarProductoPorCodigo(texto);

            if (producto.isEmpty()) {

                List<ProductoDto> coincidencias = ventaService.buscarProductos(texto);

                if (coincidencias.isEmpty()) {
                    warning("No se encontró ningún producto con \"" + texto + "\".");
                    enfocarBusqueda();
                    return;
                }

                producto = coincidencias.size() == 1
                        ? Optional.of(coincidencias.get(0))
                        : new SeleccionProductoDialog(coincidencias, texto).mostrar();
            }

            if (producto.isEmpty()) {
                enfocarBusqueda();
                return;
            }

            agregar(producto.get(), cantidad);
        });
    }

    private void agregar(ProductoDto producto, BigDecimal cantidad) {

        Renglon renglon;

        try {
            renglon = carrito.agregar(producto, cantidad);
        } catch (IllegalArgumentException e) {
            warning(e.getMessage());
            txtCantidad.requestFocus();
            txtCantidad.selectAll();
            return;
        }

        refrescarCarrito();

        tblCarrito.getSelectionModel().select(renglon);
        tblCarrito.scrollTo(renglon);

        txtBuscar.clear();
        txtCantidad.setText("1");
        enfocarBusqueda();
    }

    private void cambiarCantidad(Renglon renglon, BigDecimal cantidad) {

        try {
            carrito.cambiarCantidad(renglon, cantidad);
        } catch (IllegalArgumentException e) {
            warning(e.getMessage());
        }

        refrescarCarrito();
        tblCarrito.getSelectionModel().select(renglon);
        Platform.runLater(tblCarrito::requestFocus);
    }

    private void editarCantidad(Renglon renglon) {

        int fila = tblCarrito.getItems().indexOf(renglon);

        if (fila < 0) {
            return;
        }

        tblCarrito.requestFocus();
        tblCarrito.getSelectionModel().select(fila);
        tblCarrito.edit(fila, colCantidad);
    }

    private void quitar(Renglon renglon) {

        int fila = tblCarrito.getItems().indexOf(renglon);

        carrito.quitar(renglon);
        refrescarCarrito();

        if (tblCarrito.getItems().isEmpty()) {
            enfocarBusqueda();
            return;
        }

        tblCarrito.getSelectionModel().select(Math.min(fila, tblCarrito.getItems().size() - 1));
        tblCarrito.requestFocus();
    }

    private void refrescarCarrito() {

        tblCarrito.getItems().setAll(carrito.getRenglones());
        tblCarrito.refresh();

        List<Renglon> sinStock = carrito.sinStockSuficiente();

        mostrar(lblAvisoStock, !sinStock.isEmpty());
        lblAvisoStock.setText(sinStock.isEmpty() ? "" : "Sin stock suficiente: " + sinStock.stream()
                .map(r -> r.getProducto().getNombre()
                        + " (disponible " + CantidadUtils.formatear(r.getProducto().getStock())
                        + ", en la venta " + CantidadUtils.formatear(r.getCantidad()) + ")")
                .collect(Collectors.joining("; "))
                + ". Corrija las cantidades antes de confirmar.");

        actualizarTotales();
    }

    // ------------------------------------------------------------------
    // Cobro
    // ------------------------------------------------------------------

    private Cobro calcularCobro() {
        return CarritoVenta.cobrar(carrito.subtotal(), txtDescuento.getText(),
                cmbFormaPago.getValue() == FormaPago.EFECTIVO, txtPagaCon.getText());
    }

    private void actualizarTotales() {

        Cobro cobro = calcularCobro();

        lblSubtotal.setText(VentaFormato.importe(carrito.subtotal()));
        lblTotal.setText(VentaFormato.importe(cobro.total()));
        lblError.setText(Objects.toString(cobro.error(), ""));

        BigDecimal vuelto = cobro.vuelto();
        lblVuelto.setText(vuelto == null ? "—" : VentaFormato.importe(vuelto.max(BigDecimal.ZERO)));

        btnConfirmar.setDisable(carrito.estaVacio() || !cobro.valido());
    }

    private void confirmar() {

        if (carrito.estaVacio()) {
            enfocarBusqueda();
            return;
        }

        Cobro cobro = calcularCobro();

        if (!cobro.valido()) {
            warning(cobro.error());
            return;
        }

        boolean[] registrada = {false};

        execute(() -> {

            // Se vuelve a controlar contra el stock actual: pudo cambiar desde que se agregó el producto.
            List<String> faltantes = ventaService.verificarStock(carrito.toDetalles());

            if (!faltantes.isEmpty()) {
                Dialogs.lista(Dialogs.Tipo.ADVERTENCIA, "Stock insuficiente",
                        "No se puede confirmar la venta. Corrija las cantidades o quite estos productos:",
                        faltantes);
                return;
            }

            if (!confirm("Confirmar venta", resumen(cobro))) {
                return;
            }

            ClienteDto cliente = cmbCliente.getValue();

            VentaDto guardada = ventaService.registrar(VentaDto.builder()
                    .tipoComprobante(cmbTipoComprobante.getValue())
                    .clienteId(cliente == null ? null : cliente.getId())
                    .formaPago(cmbFormaPago.getValue())
                    .descuento(cobro.descuento())
                    .detalles(new ArrayList<>(carrito.toDetalles()))
                    .build());

            registrada[0] = true;

            nuevaVenta();

            String registro = "Se registró la venta " + guardada.getComprobante() + ".\n\n"
                    + "Total: " + VentaFormato.importe(guardada.getTotal())
                    + (cobro.vuelto() == null ? "" : "\nVuelto: " + VentaFormato.importe(cobro.vuelto()))
                    + "\n\n¿Desea imprimir el comprobante?";

            // La venta ya quedó registrada: si falla la impresión se avisa aparte
            // y se puede reimprimir desde el listado de ventas.
            if (Dialogs.confirmar("Venta registrada", registro, "Imprimir", false)) {
                execute(() -> impresionComprobante.imprimir(guardada));
            }

            enfocarBusqueda();
        });

        // Si se cerró la caja mientras tanto, la pantalla lo muestra.
        if (!registrada[0]) {
            verificarCaja();
        }
    }

    private String resumen(Cobro cobro) {

        ClienteDto cliente = cmbCliente.getValue();

        StringBuilder texto = new StringBuilder()
                .append("Comprobante: ").append(cmbTipoComprobante.getValue()).append('\n')
                .append("Cliente: ").append(cliente == null || cliente == CONSUMIDOR_FINAL
                        ? VentaDto.CONSUMIDOR_FINAL : cliente.getNombreCompleto()).append('\n')
                .append("Forma de pago: ").append(cmbFormaPago.getValue()).append('\n')
                .append("Productos: ").append(carrito.getRenglones().size()).append('\n');

        if (cobro.descuento() != null && cobro.descuento().signum() > 0) {
            texto.append("Descuento: ").append(VentaFormato.importe(cobro.descuento())).append('\n');
        }

        texto.append("Total: ").append(VentaFormato.importe(cobro.total()));

        if (cobro.vuelto() != null) {
            texto.append("\nPaga con: ").append(VentaFormato.importe(cobro.pagaCon()))
                    .append("\nVuelto: ").append(VentaFormato.importe(cobro.vuelto()));
        }

        return texto.toString();
    }

    private void vaciar() {

        if (!carrito.estaVacio()
                && !confirm("Vaciar venta", "¿Quitar todos los productos de la venta actual?")) {
            return;
        }

        nuevaVenta();
        enfocarBusqueda();
    }

    private void nuevaVenta() {

        carrito.vaciar();
        txtBuscar.clear();
        txtCantidad.setText("1");
        txtDescuento.clear();
        txtPagaCon.clear();
        cmbCliente.setValue(CONSUMIDOR_FINAL);
        cmbFormaPago.setValue(FormaPago.EFECTIVO);
        cmbTipoComprobante.setValue(TipoComprobante.TICKET);

        refrescarCarrito();
    }

    // ------------------------------------------------------------------
    // Teclado
    // ------------------------------------------------------------------

    /**
     * Atajos de la pantalla: F2 buscar, F3 cambiar cantidad, F4 cliente,
     * F5 forma de pago, F6 descuento, F7 paga con, F8 vaciar, F9 comprobante,
     * F12 confirmar.
     */
    private void atajo(KeyEvent e) {

        if (!panelVenta.isVisible()) {
            return;
        }

        switch (e.getCode()) {
            case F2 -> enfocarBusqueda();
            case F3 -> {
                Renglon seleccionado = tblCarrito.getSelectionModel().getSelectedItem();
                if (seleccionado != null) {
                    editarCantidad(seleccionado);
                } else if (!tblCarrito.getItems().isEmpty()) {
                    editarCantidad(tblCarrito.getItems().get(tblCarrito.getItems().size() - 1));
                }
            }
            case F4 -> cmbCliente.getEditor().requestFocus();
            case F5 -> {
                cmbFormaPago.requestFocus();
                cmbFormaPago.show();
            }
            case F6 -> enfocar(txtDescuento);
            case F7 -> {
                if (panelEfectivo.isVisible()) {
                    enfocar(txtPagaCon);
                }
            }
            case F8 -> vaciar();
            case F9 -> {
                cmbTipoComprobante.requestFocus();
                cmbTipoComprobante.show();
            }
            case F12 -> confirmar();
            default -> {
                return;
            }
        }

        e.consume();
    }

    /** Supr quita el renglón; + y − suman o restan uno a la cantidad. */
    private void teclaEnTabla(KeyEvent e) {

        Renglon renglon = tblCarrito.getSelectionModel().getSelectedItem();

        if (renglon == null || tblCarrito.getEditingCell() != null) {
            return;
        }

        if (e.getCode() == KeyCode.DELETE) {
            e.consume();
            quitar(renglon);
            return;
        }

        boolean mas = e.getCode() == KeyCode.ADD || e.getCode() == KeyCode.PLUS || "+".equals(e.getText());
        boolean menos = e.getCode() == KeyCode.SUBTRACT || e.getCode() == KeyCode.MINUS || "-".equals(e.getText());

        if (mas || menos) {
            e.consume();
            if (carrito.sumar(renglon, mas ? BigDecimal.ONE : BigDecimal.ONE.negate())) {
                refrescarCarrito();
                tblCarrito.getSelectionModel().select(renglon);
            }
        }
    }

    private void enfocarBusqueda() {
        Platform.runLater(() -> enfocar(txtBuscar));
    }

    private void enfocarTabla() {
        tblCarrito.requestFocus();
        if (tblCarrito.getSelectionModel().isEmpty()) {
            tblCarrito.getSelectionModel().selectLast();
        }
    }

    private static void enfocar(TextField campo) {
        campo.requestFocus();
        campo.selectAll();
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }
}
