package org.gestionComercio.controller.compra;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.base.ErroresPorCampo;
import org.gestionComercio.controller.compra.RenglonesCompra.Renglon;
import org.gestionComercio.controller.producto.ProductoFormController;
import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.enums.EstadoCompra;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.CompraService;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.NumberUtils;
import org.gestionComercio.util.dialog.Dialogs;
import org.gestionComercio.validation.ValidationError;
import org.gestionComercio.validation.ValidationResult;
import org.gestionComercio.view.component.ComboBoxBusqueda;
import org.gestionComercio.view.component.SelectorFecha;
import org.gestionComercio.view.component.SeleccionProductoDialog;
import org.gestionComercio.view.component.table.RowActionsTableCell;
import org.gestionComercio.view.component.table.RowActionsTableCell.Accion;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Registro de una compra a un proveedor con {@link CompraService#registrar}.
 * Los productos se buscan por código o nombre y se agregan con su cantidad y
 * costo (vacío = el precio de compra actual del producto); en la tabla se
 * pueden corregir la cantidad y el costo de cada renglón.
 *
 * <p>Los errores del servicio se muestran debajo de cada campo; los de los
 * renglones, juntos debajo de la tabla.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class CompraFormController extends AbstractController {

    /** Campo del formulario donde se muestran los errores de todos los renglones. */
    private static final String CAMPO_DETALLES = "detalles";

    @FXML
    private Button btnListado;

    @FXML
    private ComboBox<ProveedorDto> cmbProveedor;

    @FXML
    private Label lblErrorProveedor;

    @FXML
    private TextField txtNumeroFactura;

    @FXML
    private Label lblErrorNumeroFactura;

    @FXML
    private DatePicker dpFecha;

    @FXML
    private Label lblErrorFecha;

    @FXML
    private ComboBox<FormaPago> cmbFormaPago;

    @FXML
    private Label lblErrorFormaPago;

    @FXML
    private CheckBox chkPendiente;

    @FXML
    private CheckBox chkActualizarPrecios;

    @FXML
    private Label lblAvisoEfectivo;

    @FXML
    private TextField txtBuscar;

    @FXML
    private TextField txtCantidad;

    @FXML
    private TextField txtCosto;

    @FXML
    private Button btnAgregar;

    @FXML
    private TableView<Renglon> tblRenglones;

    @FXML
    private TableColumn<Renglon, String> colCodigo;

    @FXML
    private TableColumn<Renglon, String> colProducto;

    @FXML
    private TableColumn<Renglon, BigDecimal> colCantidad;

    @FXML
    private TableColumn<Renglon, BigDecimal> colCosto;

    @FXML
    private TableColumn<Renglon, String> colCostoActual;

    @FXML
    private TableColumn<Renglon, String> colSubtotal;

    @FXML
    private TableColumn<Renglon, Void> colAcciones;

    @FXML
    private Label lblErrorDetalles;

    @FXML
    private Label lblCantidadRenglones;

    @FXML
    private Label lblTotal;

    @FXML
    private Button btnCancelar;

    @FXML
    private Button btnGuardar;

    private final CompraService compraService;
    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;

    private final RenglonesCompra renglones = new RenglonesCompra();
    private final ErroresPorCampo errores = new ErroresPorCampo();

    @Override
    protected void initializeComponents() {

        configurarProveedor();
        configurarTabla();

        SelectorFecha.configurar(dpFecha);
        dpFecha.setValue(LocalDate.now());

        cmbFormaPago.getItems().setAll(FormaPago.values());
        cmbFormaPago.setValue(FormaPago.TRANSFERENCIA);

        txtNumeroFactura.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().length() <= CompraFormato.NUMERO_FACTURA_MAX ? cambio : null));

        errores.registrar("proveedorId", cmbProveedor, lblErrorProveedor, cmbProveedor.valueProperty())
                .registrar("numeroFactura", txtNumeroFactura, lblErrorNumeroFactura, txtNumeroFactura.textProperty())
                .registrar("fecha", dpFecha, lblErrorFecha, dpFecha.valueProperty())
                .registrar("formaPago", cmbFormaPago, lblErrorFormaPago, cmbFormaPago.valueProperty())
                .registrar(CAMPO_DETALLES, tblRenglones, lblErrorDetalles);

        boolean puedeVerListado = navigator.puedeAcceder(AppView.COMPRA_LIST);
        btnListado.setVisible(puedeVerListado);
        btnListado.setManaged(puedeVerListado);

        actualizarAvisoEfectivo();
        refrescar();

        Platform.runLater(() -> cmbProveedor.getEditor().requestFocus());
    }

    @Override
    protected void initializeEvents() {

        txtBuscar.setOnAction(e -> agregarDesdeBusqueda());
        txtCantidad.setOnAction(e -> agregarDesdeBusqueda());
        txtCosto.setOnAction(e -> agregarDesdeBusqueda());
        btnAgregar.setOnAction(e -> agregarDesdeBusqueda());

        cmbFormaPago.valueProperty().addListener((obs, o, n) -> actualizarAvisoEfectivo());

        tblRenglones.setOnKeyPressed(e -> {
            Renglon seleccionado = tblRenglones.getSelectionModel().getSelectedItem();
            if (e.getCode() == KeyCode.DELETE && seleccionado != null && tblRenglones.getEditingCell() == null) {
                e.consume();
                quitar(seleccionado);
            }
        });

        btnGuardar.setOnAction(e -> guardar());
        btnCancelar.setOnAction(e -> cancelar());
        btnListado.setOnAction(e -> salir(AppView.COMPRA_LIST));
    }

    // ------------------------------------------------------------------
    // Configuración
    // ------------------------------------------------------------------

    private void configurarProveedor() {

        List<ProveedorDto> proveedores = new ArrayList<>();

        execute(() -> proveedores.addAll(compraService.listarProveedores()));

        ComboBoxBusqueda.configurar(cmbProveedor,
                        p -> p.getCuit() == null || p.getCuit().isBlank()
                                ? p.getRazonSocial()
                                : p.getRazonSocial() + " — " + p.getCuitFormateado(),
                        p -> p.getRazonSocial() + " " + Objects.toString(p.getCuit(), ""))
                .setAll(proveedores);

        cmbProveedor.setVisibleRowCount(12);
        cmbProveedor.setPromptText("Buscar proveedor por razón social o CUIT...");
    }

    private void configurarTabla() {

        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProducto().getCodigo()));
        colProducto.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProducto().getNombre()));
        colCostoActual.setCellValueFactory(c -> new SimpleStringProperty(
                CompraFormato.importe(c.getValue().getProducto().getPrecioCompra())));
        colSubtotal.setCellValueFactory(c -> new SimpleStringProperty(CompraFormato.importe(c.getValue().getSubtotal())));

        colCantidad.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getCantidad()));
        colCantidad.setCellFactory(TextFieldTableCell.forTableColumn(conversor(CantidadUtils::formatearEditable)));
        colCantidad.setOnEditCommit(e -> editar(e.getRowValue(), () -> renglones.cambiarCantidad(e.getRowValue(), e.getNewValue())));

        colCosto.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getPrecio()));
        colCosto.setCellFactory(TextFieldTableCell.forTableColumn(conversor(NumberUtils::formatDecimalEditable)));
        colCosto.setOnEditCommit(e -> editar(e.getRowValue(), () -> renglones.cambiarPrecio(e.getRowValue(), e.getNewValue())));

        colAcciones.setCellFactory(column -> new RowActionsTableCell<>(List.of(
                new Accion<Renglon>(FontAwesomeSolid.TRASH, "Quitar (Supr)", "btn-icon-delete",
                        r -> true, this::quitar))));
    }

    /** Muestra el número editable y lee lo escrito; un texto inválido se lee como null (lo rechaza el renglón). */
    private static StringConverter<BigDecimal> conversor(Function<BigDecimal, String> formato) {

        return new StringConverter<>() {

            @Override
            public String toString(BigDecimal valor) {
                return valor == null ? "" : formato.apply(valor);
            }

            @Override
            public BigDecimal fromString(String texto) {
                try {
                    return NumberUtils.parseDecimal(texto);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        };
    }

    private void actualizarAvisoEfectivo() {
        boolean efectivo = cmbFormaPago.getValue() == FormaPago.EFECTIVO;
        lblAvisoEfectivo.setVisible(efectivo);
        lblAvisoEfectivo.setManaged(efectivo);
    }

    // ------------------------------------------------------------------
    // Renglones
    // ------------------------------------------------------------------

    /**
     * Busca lo escrito. Si coincide exactamente con un código de barras o un
     * código interno, o hay una sola coincidencia, lo agrega; con varias deja elegir.
     */
    private void agregarDesdeBusqueda() {

        String texto = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();

        if (texto.isEmpty()) {
            enfocar(txtBuscar);
            return;
        }

        BigDecimal cantidad;
        BigDecimal costo;

        try {
            cantidad = Objects.requireNonNullElse(NumberUtils.parseDecimal(txtCantidad.getText()), BigDecimal.ONE);
        } catch (NumberFormatException e) {
            warning("Ingrese una cantidad válida.");
            enfocar(txtCantidad);
            return;
        }

        try {
            costo = CompraFormato.leerImporte(txtCosto.getText(), "El costo");
        } catch (IllegalArgumentException e) {
            warning(e.getMessage());
            enfocar(txtCosto);
            return;
        }

        execute(() -> {

            List<ProductoDto> coincidencias = compraService.buscarProductos(texto);

            Optional<ProductoDto> producto = coincidencias.stream()
                    .filter(p -> texto.equals(p.getCodigoBarras()) || texto.equalsIgnoreCase(p.getCodigo()))
                    .findFirst();

            if (producto.isEmpty()) {

                if (coincidencias.isEmpty()) {
                    ofrecerAlta(texto, cantidad, costo);
                    return;
                }

                producto = coincidencias.size() == 1
                        ? Optional.of(coincidencias.get(0))
                        : new SeleccionProductoDialog(coincidencias, texto, "Costo actual",
                        ProductoDto::getPrecioCompra).mostrar();
            }

            producto.ifPresentOrElse(p -> agregar(p, cantidad, costo), () -> enfocar(txtBuscar));
        });
    }

    /**
     * El artículo buscado no está cargado: ofrece darlo de alta en una ventana
     * aparte (sin perder la compra) y, si se crea, lo agrega a la compra con la
     * cantidad y el costo indicados. Sin PRODUCTO_CREAR sólo lo informa.
     */
    private void ofrecerAlta(String texto, BigDecimal cantidad, BigDecimal costo) {

        if (!sesionUsuario.tienePermiso(PermisoCodigo.PRODUCTO_CREAR)) {
            warning("No se encuentra ese artículo cargado en el sistema. "
                    + "Pida a un usuario con permiso para crear productos que lo cargue.");
            enfocar(txtBuscar);
            return;
        }

        if (!Dialogs.confirmar("Artículo no encontrado",
                "No se encuentra ese artículo cargado en el sistema. ¿Desea cargarlo?",
                "Cargar artículo", false)) {
            enfocar(txtBuscar);
            return;
        }

        ProveedorDto proveedor = cmbProveedor.getValue();

        ProductoDto sugerido = ProductoDto.builder()
                .codigoBarras(pareceCodigoDeBarras(texto) ? texto : null)
                .nombre(pareceCodigoDeBarras(texto) ? null : texto)
                .proveedorId(proveedor == null ? null : proveedor.getId())
                .build();

        navigator.<ProductoFormController>abrirModal(AppView.PRODUCTO_FORM,
                formulario -> formulario.prepararAlta(sugerido, creado -> agregar(creado, cantidad, costo)));

        if (!txtBuscar.getText().isBlank()) {
            enfocar(txtBuscar);
        }
    }

    /** Lo que deja un lector de código de barras: sólo dígitos, al menos ocho (EAN-8, EAN-13, UPC). */
    static boolean pareceCodigoDeBarras(String texto) {
        return texto != null && texto.matches("\\d{8,}");
    }

    /** @param costo null para usar el precio de compra actual del producto */
    private void agregar(ProductoDto producto, BigDecimal cantidad, BigDecimal costo) {

        BigDecimal precio = costo != null
                ? costo
                : Objects.requireNonNullElse(producto.getPrecioCompra(), BigDecimal.ZERO);

        Renglon renglon;

        try {
            renglon = renglones.agregar(producto, cantidad, precio);
        } catch (IllegalArgumentException e) {
            warning(e.getMessage());
            enfocar(txtCantidad);
            return;
        }

        refrescar();

        tblRenglones.getSelectionModel().select(renglon);
        tblRenglones.scrollTo(renglon);

        txtBuscar.clear();
        txtCantidad.setText("1");
        txtCosto.clear();
        Platform.runLater(() -> enfocar(txtBuscar));
    }

    private void editar(Renglon renglon, Runnable cambio) {

        try {
            cambio.run();
        } catch (IllegalArgumentException e) {
            warning(e.getMessage());
        }

        refrescar();
        tblRenglones.getSelectionModel().select(renglon);
        Platform.runLater(tblRenglones::requestFocus);
    }

    private void quitar(Renglon renglon) {
        renglones.quitar(renglon);
        refrescar();
    }

    private void refrescar() {

        tblRenglones.getItems().setAll(renglones.getRenglones());
        tblRenglones.refresh();

        int cantidad = renglones.getRenglones().size();
        lblCantidadRenglones.setText(cantidad + (cantidad == 1 ? " producto" : " productos"));
        lblTotal.setText(CompraFormato.importe(renglones.total()));

        errores.limpiar(CAMPO_DETALLES);
        btnGuardar.setDisable(renglones.estaVacio());
    }

    // ------------------------------------------------------------------
    // Guardar
    // ------------------------------------------------------------------

    private void guardar() {

        errores.limpiar();

        if (dpFecha.getValue() == null) {
            errores.mostrar("fecha", "Indique la fecha de la compra.");
            dpFecha.requestFocus();
            return;
        }

        if (renglones.estaVacio()) {
            enfocar(txtBuscar);
            return;
        }

        CompraDto compra = armarCompra();

        if (!confirm("Confirmar compra", resumen(compra))) {
            return;
        }

        execute(() -> {

            CompraDto guardada;

            try {
                guardada = compraService.registrar(compra);
            } catch (ValidationException ex) {
                mostrarErrores(ex.getValidationResult());
                return;
            }

            success("Se registró la " + CompraFormato.titulo(guardada).toLowerCase()
                    + " por " + CompraFormato.importe(guardada.getTotal()) + "."
                    + (guardada.getEstadoCompra() == EstadoCompra.PENDIENTE
                    ? "\n\nQuedó pendiente: el stock se sumará cuando la marque como recibida."
                    : ""));

            if (navigator.puedeAcceder(AppView.COMPRA_LIST)) {
                navigator.navigate(AppView.COMPRA_LIST);
            } else {
                nuevaCompra();
            }
        });
    }

    private CompraDto armarCompra() {

        ProveedorDto proveedor = cmbProveedor.getValue();
        LocalDate fecha = dpFecha.getValue();

        return CompraDto.builder()
                .proveedorId(proveedor == null ? null : proveedor.getId())
                .numeroFactura(txtNumeroFactura.getText())
                // La compra de hoy queda con la hora actual; una anterior, al comienzo de ese día.
                .fecha(fecha.equals(LocalDate.now()) ? null : fecha.atStartOfDay())
                .formaPago(cmbFormaPago.getValue())
                .estadoCompra(chkPendiente.isSelected() ? EstadoCompra.PENDIENTE : EstadoCompra.RECIBIDA)
                .actualizarPreciosCompra(chkActualizarPrecios.isSelected())
                .detalles(new ArrayList<>(renglones.toDetalles()))
                .build();
    }

    private String resumen(CompraDto compra) {

        ProveedorDto proveedor = cmbProveedor.getValue();

        StringBuilder texto = new StringBuilder()
                .append("Proveedor: ").append(proveedor == null ? "(sin elegir)" : proveedor.getRazonSocial()).append('\n')
                .append("Factura: ").append(CompraFormato.factura(compra.getNumeroFactura())).append('\n')
                .append("Forma de pago: ").append(Objects.toString(compra.getFormaPago(), "")).append('\n')
                .append("Productos: ").append(renglones.getRenglones().size()).append('\n')
                .append("Total: ").append(CompraFormato.importe(renglones.total()));

        texto.append("\n\n").append(compra.getEstadoCompra() == EstadoCompra.PENDIENTE
                ? "La mercadería queda pendiente: el stock no cambia hasta recibirla."
                : "Se sumará al stock lo comprado.");

        if (compra.getFormaPago() == FormaPago.EFECTIVO && renglones.total().signum() > 0) {
            texto.append("\nSe registrará un pago de ").append(CompraFormato.importe(renglones.total()))
                    .append(" en su caja.");
        }

        if (compra.isActualizarPreciosCompra() && renglones.hayCambiosDePrecio()) {
            texto.append("\nSe actualizará el precio de compra de los productos con costo distinto al actual.");
        }

        return texto.toString();
    }

    /**
     * Los errores de los renglones ({@code detalles[i].campo}) se muestran
     * todos juntos debajo de la tabla; el resto, debajo de su campo.
     */
    private void mostrarErrores(ValidationResult result) {

        ValidationResult ubicados = new ValidationResult();

        for (ValidationError error : result.getErrors()) {
            String campo = error.getField() != null && error.getField().startsWith(CAMPO_DETALLES)
                    ? CAMPO_DETALLES
                    : error.getField();
            ubicados.addError(campo, error.getMessage());
        }

        errores.mostrar(ubicados);
    }

    /** Vuelve al listado; sin acceso al listado, deja el formulario en blanco. */
    private void cancelar() {

        if (navigator.puedeAcceder(AppView.COMPRA_LIST)) {
            salir(AppView.COMPRA_LIST);
        } else if (confirmarDescarte()) {
            nuevaCompra();
        }
    }

    /** Va a otra pantalla, confirmando antes si hay renglones cargados. */
    private void salir(AppView destino) {
        if (confirmarDescarte()) {
            navigator.navigate(destino);
        }
    }

    /** @return true si no hay nada cargado o el usuario acepta descartarlo. */
    private boolean confirmarDescarte() {
        return renglones.estaVacio() || confirm("Descartar compra", "¿Descartar la compra que está cargando?");
    }

    private void nuevaCompra() {

        renglones.vaciar();
        cmbProveedor.setValue(null);
        txtNumeroFactura.clear();
        dpFecha.setValue(LocalDate.now());
        cmbFormaPago.setValue(FormaPago.TRANSFERENCIA);
        chkPendiente.setSelected(false);
        chkActualizarPrecios.setSelected(false);
        txtBuscar.clear();
        txtCantidad.setText("1");
        txtCosto.clear();
        errores.limpiar();

        refrescar();
        Platform.runLater(() -> cmbProveedor.getEditor().requestFocus());
    }

    private static void enfocar(TextField campo) {
        campo.requestFocus();
        campo.selectAll();
    }
}
