package org.gestionComercio.controller.producto;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.base.ErroresPorCampo;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.enums.AlicuotaIVA;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.CategoriaService;
import org.gestionComercio.service.MarcaService;
import org.gestionComercio.service.ProductoService;
import org.gestionComercio.service.ProveedorService;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.NumberUtils;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.function.Consumer;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class ProductoFormController extends AbstractController implements DataReceiver<ProductoDto> {

    private final ProductoService productoService;
    private final CategoriaService categoriaService;
    private final MarcaService marcaService;
    private final ProveedorService proveedorService;
    private final Navigator navigator;

    @FXML
    private Label lblTitulo;

    @FXML
    private Label lblAvisoDadoDeBaja;

    @FXML
    private TextField txtCodigo;

    @FXML
    private Label lblErrorCodigo;

    @FXML
    private TextField txtCodigoBarras;

    @FXML
    private Label lblErrorCodigoBarras;

    @FXML
    private TextField txtNombre;

    @FXML
    private Label lblErrorNombre;

    @FXML
    private TextArea txtDescripcion;

    @FXML
    private Label lblErrorDescripcion;

    @FXML
    private ComboBox<Opcion> cmbCategoria;

    @FXML
    private Label lblErrorCategoria;

    @FXML
    private ComboBox<Opcion> cmbMarca;

    @FXML
    private Label lblErrorMarca;

    @FXML
    private ComboBox<Opcion> cmbProveedor;

    @FXML
    private Label lblErrorProveedor;

    @FXML
    private TextField txtPrecioCompra;

    @FXML
    private Label lblErrorPrecioCompra;

    @FXML
    private TextField txtPrecioVenta;

    @FXML
    private Label lblErrorPrecioVenta;

    @FXML
    private Label lblMargen;

    @FXML
    private ComboBox<UnidadMedida> cmbUnidadMedida;

    @FXML
    private Label lblErrorUnidadMedida;

    @FXML
    private ComboBox<AlicuotaIVA> cmbAlicuotaIva;

    @FXML
    private Label lblErrorAlicuotaIva;

    @FXML
    private CheckBox chkManejaStock;

    @FXML
    private TextField txtStockMinimo;

    @FXML
    private Label lblErrorStockMinimo;

    @FXML
    private CheckBox chkPermiteStockNegativo;

    @FXML
    private VBox boxStockActual;

    @FXML
    private Label lblStockActual;

    @FXML
    private Button btnGuardar;

    @FXML
    private Button btnCancelar;

    /** null = alta; distinto de null = edición. */
    private ProductoDto productoActual;

    /**
     * Alta pedida desde otra pantalla, en una ventana aparte (ver
     * {@link #prepararAlta}): recibe el producto creado. null = uso normal,
     * desde el ABM de productos.
     */
    private Consumer<ProductoDto> alCrear;

    private final ErroresPorCampo errores = new ErroresPorCampo();

    /** Elemento de los combos de categoría, marca y proveedor. */
    private record Opcion(Long id, String texto) {

        @Override
        public String toString() {
            return texto;
        }
    }

    @Override
    protected void initializeComponents() {

        errores
                .registrar("codigo", txtCodigo, lblErrorCodigo, txtCodigo.textProperty())
                .registrar("codigoBarras", txtCodigoBarras, lblErrorCodigoBarras, txtCodigoBarras.textProperty())
                .registrar("nombre", txtNombre, lblErrorNombre, txtNombre.textProperty())
                .registrar("descripcion", txtDescripcion, lblErrorDescripcion, txtDescripcion.textProperty())
                .registrar("categoriaId", cmbCategoria, lblErrorCategoria, cmbCategoria.valueProperty())
                .registrar("marcaId", cmbMarca, lblErrorMarca, cmbMarca.valueProperty())
                .registrar("proveedorId", cmbProveedor, lblErrorProveedor, cmbProveedor.valueProperty())
                .registrar("precioCompra", txtPrecioCompra, lblErrorPrecioCompra, txtPrecioCompra.textProperty())
                .registrar("precioVenta", txtPrecioVenta, lblErrorPrecioVenta, txtPrecioVenta.textProperty())
                .registrar("unidadMedida", cmbUnidadMedida, lblErrorUnidadMedida, cmbUnidadMedida.valueProperty())
                .registrar("alicuotaIVA", cmbAlicuotaIva, lblErrorAlicuotaIva, cmbAlicuotaIva.valueProperty())
                .registrar("stockMinimo", txtStockMinimo, lblErrorStockMinimo, txtStockMinimo.textProperty());

        cargarCombos();

        lblTitulo.setText("Nuevo producto");
        cmbUnidadMedida.setValue(UnidadMedida.UNIDAD);
        cmbAlicuotaIva.setValue(AlicuotaIVA.IVA_21);
        chkManejaStock.setSelected(true);
        txtStockMinimo.setText("0");

        mostrar(lblAvisoDadoDeBaja, false);
        mostrar(boxStockActual, false);
        actualizarMargen();

        txtCodigo.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnCancelar.setOnAction(e -> volver());

        // Los lectores de código de barras terminan con Enter: en vez de guardar
        // el formulario (botón por defecto), se pasa al campo siguiente.
        txtCodigoBarras.setOnAction(e -> {
            e.consume();
            txtNombre.requestFocus();
        });

        txtPrecioCompra.textProperty().addListener((obs, o, n) -> actualizarMargen());
        txtPrecioVenta.textProperty().addListener((obs, o, n) -> actualizarMargen());

        chkManejaStock.selectedProperty().addListener((obs, o, n) -> actualizarControlesStock());
        actualizarControlesStock();
    }

    /**
     * Los combos ofrecen sólo categorías, marcas y proveedores vigentes. Los
     * servicios aceptan PRODUCTO_CREAR o PRODUCTO_EDITAR, así que no hace falta
     * tener acceso a esos ABM.
     */
    private void cargarCombos() {

        execute(() -> {

            cmbCategoria.getItems().setAll(categoriaService.findParaSeleccion().stream()
                    .map(c -> new Opcion(c.getId(), c.getNombre()))
                    .toList());

            cmbMarca.getItems().setAll(marcaService.findParaSeleccion().stream()
                    .map(m -> new Opcion(m.getId(), m.getNombre()))
                    .toList());

            cmbProveedor.getItems().setAll(proveedorService.findParaSeleccion().stream()
                    .map(p -> new Opcion(p.getId(), p.getRazonSocial()))
                    .toList());
        });

        cmbUnidadMedida.getItems().setAll(UnidadMedida.values());
        cmbAlicuotaIva.getItems().setAll(AlicuotaIVA.values());
    }

    @Override
    public void setData(ProductoDto producto) {

        productoActual = producto;

        lblTitulo.setText("Editar producto");
        txtCodigo.setText(producto.getCodigo());
        txtCodigoBarras.setText(producto.getCodigoBarras());
        txtNombre.setText(producto.getNombre());
        txtDescripcion.setText(producto.getDescripcion());
        txtPrecioCompra.setText(NumberUtils.formatDecimalEditable(producto.getPrecioCompra()));
        txtPrecioVenta.setText(NumberUtils.formatDecimalEditable(producto.getPrecioVenta()));
        cmbUnidadMedida.setValue(producto.getUnidadMedida());
        cmbAlicuotaIva.setValue(producto.getAlicuotaIVA());
        chkManejaStock.setSelected(Boolean.TRUE.equals(producto.getManejaStock()));
        chkPermiteStockNegativo.setSelected(Boolean.TRUE.equals(producto.getPermiteStockNegativo()));
        txtStockMinimo.setText(CantidadUtils.formatearEditable(producto.getStockMinimo()));

        seleccionarCategoria(producto);

        // Una marca o un proveedor dados de baja se conservan: se agregan al combo
        // para que el producto pueda guardarse sin cambiarlos.
        seleccionar(cmbMarca, producto.getMarcaId(), producto.getMarcaNombre(), "dada de baja");
        seleccionar(cmbProveedor, producto.getProveedorId(), producto.getProveedorNombre(), "dado de baja");

        lblStockActual.setText(Boolean.TRUE.equals(producto.getManejaStock())
                ? CantidadUtils.formatear(producto.getStock())
                : "No maneja stock");
        mostrar(boxStockActual, true);

        if (!producto.isActivo()) {
            lblAvisoDadoDeBaja.setText("Este producto está dado de baja. Guardar los cambios no lo "
                    + "habilita: para eso use \"Volver a habilitar\" en el listado.");
            mostrar(lblAvisoDadoDeBaja, true);
        }
    }

    /**
     * Prepara el alta de un producto pedida desde otra pantalla (por ejemplo,
     * al buscar en una compra un artículo que no está cargado). Completa los
     * datos sugeridos; al guardar, cierra la ventana y entrega el producto
     * creado a {@code alCrear}. Cancelar sólo cierra la ventana.
     *
     * @param sugerido código de barras, nombre y proveedor a completar (cualquiera puede ser null)
     */
    public void prepararAlta(ProductoDto sugerido, Consumer<ProductoDto> alCrear) {

        this.alCrear = Objects.requireNonNull(alCrear);

        if (sugerido == null) {
            return;
        }

        txtCodigoBarras.setText(sugerido.getCodigoBarras());
        txtNombre.setText(sugerido.getNombre());

        Opcion proveedor = buscarOpcion(cmbProveedor, sugerido.getProveedorId());
        if (proveedor != null) {
            cmbProveedor.setValue(proveedor);
        }
    }

    /** Una categoría eliminada no se puede conservar: se avisa para que elija otra. */
    private void seleccionarCategoria(ProductoDto producto) {

        Opcion opcion = buscarOpcion(cmbCategoria, producto.getCategoriaId());

        if (opcion != null) {
            cmbCategoria.setValue(opcion);
            return;
        }

        if (producto.getCategoriaId() != null) {
            errores.mostrar("categoriaId", "La categoría \"" + producto.getCategoriaNombre()
                    + "\" fue eliminada. Seleccione otra.");
        }
    }

    private void seleccionar(ComboBox<Opcion> combo, Long id, String nombre, String sufijoBaja) {

        if (id == null) {
            return;
        }

        Opcion opcion = buscarOpcion(combo, id);

        if (opcion == null) {
            opcion = new Opcion(id, nombre + " (" + sufijoBaja + ")");
            combo.getItems().add(0, opcion);
        }

        combo.setValue(opcion);
    }

    private Opcion buscarOpcion(ComboBox<Opcion> combo, Long id) {

        return combo.getItems().stream()
                .filter(o -> Objects.equals(o.id(), id))
                .findFirst()
                .orElse(null);
    }

    private void guardar() {

        errores.limpiar();

        // Primero se revisa que los números se puedan interpretar; el resto lo
        // valida el servicio.
        ValidationResult erroresDeFormato = new ValidationResult();
        ProductoDto dto = construirDto(erroresDeFormato);

        if (erroresDeFormato.hasErrors()) {
            errores.mostrar(erroresDeFormato);
            return;
        }

        execute(() -> {

            try {

                if (productoActual == null && alCrear != null) {
                    ProductoDto creado = productoService.save(dto);
                    cerrarVentana();
                    alCrear.accept(creado);
                    return;
                }

                if (productoActual == null) {
                    productoService.save(dto);
                    success("Producto creado correctamente.");
                } else {
                    productoService.update(dto);
                    success("Producto actualizado correctamente.");
                }

            } catch (ValidationException ex) {
                errores.mostrar(ex.getValidationResult());
                return;
            }

            volver();
        });
    }

    private ProductoDto construirDto(ValidationResult erroresDeFormato) {

        return ProductoDto.builder()
                .id(productoActual != null ? productoActual.getId() : null)
                .version(productoActual != null ? productoActual.getVersion() : null)
                .codigo(txtCodigo.getText())
                .codigoBarras(txtCodigoBarras.getText())
                .nombre(txtNombre.getText())
                .descripcion(txtDescripcion.getText())
                .categoriaId(idSeleccionado(cmbCategoria))
                .marcaId(idSeleccionado(cmbMarca))
                .proveedorId(idSeleccionado(cmbProveedor))
                .precioCompra(leerPrecio(txtPrecioCompra, "precioCompra", "precio de compra", erroresDeFormato))
                .precioVenta(leerPrecio(txtPrecioVenta, "precioVenta", "precio de venta", erroresDeFormato))
                .unidadMedida(cmbUnidadMedida.getValue())
                .alicuotaIVA(cmbAlicuotaIva.getValue())
                .manejaStock(chkManejaStock.isSelected())
                .permiteStockNegativo(chkPermiteStockNegativo.isSelected())
                .stockMinimo(leerStockMinimo(erroresDeFormato))
                .build();
    }

    private Long idSeleccionado(ComboBox<Opcion> combo) {
        return combo.getValue() == null ? null : combo.getValue().id();
    }

    private BigDecimal leerPrecio(TextField campo, String field, String nombre, ValidationResult erroresDeFormato) {

        try {
            return NumberUtils.parseDecimal(campo.getText());
        } catch (NumberFormatException ex) {
            erroresDeFormato.addError(field, "Ingrese un " + nombre + " válido. Ej.: 1234,50");
            return null;
        }
    }

    /** Si la unidad admite decimales o no lo valida el servicio. */
    private BigDecimal leerStockMinimo(ValidationResult erroresDeFormato) {

        if (!chkManejaStock.isSelected()) {
            return BigDecimal.ZERO;
        }

        try {
            return NumberUtils.parseDecimal(txtStockMinimo.getText());
        } catch (NumberFormatException ex) {
            erroresDeFormato.addError("stockMinimo", "Ingrese un número válido. Ej.: 10 o 2,5");
            return null;
        }
    }

    /** Muestra el margen sobre el precio de compra mientras se escriben los precios. */
    private void actualizarMargen() {

        try {

            BigDecimal compra = NumberUtils.parseDecimal(txtPrecioCompra.getText());
            BigDecimal venta = NumberUtils.parseDecimal(txtPrecioVenta.getText());

            if (compra == null || venta == null || compra.signum() <= 0) {
                lblMargen.setText("");
                return;
            }

            BigDecimal margen = venta.subtract(compra)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(compra, 1, RoundingMode.HALF_UP);

            lblMargen.setText("Margen sobre el costo: "
                    + margen.toPlainString().replace('.', ',') + " %");

        } catch (NumberFormatException ex) {
            lblMargen.setText("");
        }
    }

    private void actualizarControlesStock() {

        boolean manejaStock = chkManejaStock.isSelected();

        txtStockMinimo.setDisable(!manejaStock);
        chkPermiteStockNegativo.setDisable(!manejaStock);

        if (!manejaStock) {
            errores.limpiar("stockMinimo");
        }
    }

    private void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    /** En el alta desde otra pantalla cierra la ventana; si no, vuelve al listado de productos. */
    private void volver() {

        if (alCrear != null) {
            cerrarVentana();
            return;
        }

        navigator.navigate(AppView.PRODUCTO_LIST);
    }

    private void cerrarVentana() {
        btnCancelar.getScene().getWindow().hide();
    }
}
