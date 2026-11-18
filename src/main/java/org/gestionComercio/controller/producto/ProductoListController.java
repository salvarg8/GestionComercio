package org.gestionComercio.controller.producto;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ProductoService;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.dialog.Dialogs;
import org.gestionComercio.view.component.table.RowActionsTableCell;
import org.gestionComercio.view.component.table.RowActionsTableCell.Accion;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class ProductoListController extends AbstractController {

    private static final PseudoClass DADO_DE_BAJA = PseudoClass.getPseudoClass("dado-de-baja");

    @FXML
    private ComboBox<FiltroEstado> cmbEstado;

    @FXML
    private TextField txtBuscar;

    @FXML
    private Button btnNuevo;

    @FXML
    private TableView<ProductoDto> tblProductos;

    @FXML
    private TableColumn<ProductoDto, String> colCodigo;

    @FXML
    private TableColumn<ProductoDto, String> colCodigoBarras;

    @FXML
    private TableColumn<ProductoDto, String> colNombre;

    @FXML
    private TableColumn<ProductoDto, String> colCategoria;

    @FXML
    private TableColumn<ProductoDto, String> colMarca;

    @FXML
    private TableColumn<ProductoDto, String> colPrecioCompra;

    @FXML
    private TableColumn<ProductoDto, String> colPrecioVenta;

    @FXML
    private TableColumn<ProductoDto, ProductoDto> colStock;

    @FXML
    private TableColumn<ProductoDto, ProductoDto> colEstado;

    @FXML
    private TableColumn<ProductoDto, Void> colAcciones;

    @FXML
    private Label lblSinResultados;

    @FXML
    private Label lblTotal;

    @FXML
    private Label lblStockBajo;

    private final ProductoService productoService;
    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;

    @Override
    protected void initializeComponents() {

        boolean puedeCrear = sesionUsuario.tienePermiso(PermisoCodigo.PRODUCTO_CREAR);
        btnNuevo.setVisible(puedeCrear);
        btnNuevo.setManaged(puedeCrear);

        configurarFiltroEstado();
        configurarColumnas();
        cargarProductos();
        txtBuscar.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnNuevo.setOnAction(e -> nuevo());

        txtBuscar.textProperty().addListener((obs, oldValue, newValue) -> cargarProductos());

        // Con lector de código de barras: si el código coincide con un único
        // producto, Enter lo abre directamente para editar.
        txtBuscar.setOnAction(e -> editarUnicoResultado());

        cmbEstado.valueProperty().addListener((obs, oldValue, newValue) -> cargarProductos());

        tblProductos.setOnMouseClicked(e -> {
            ProductoDto seleccionado = tblProductos.getSelectionModel().getSelectedItem();
            if (e.getClickCount() == 2 && seleccionado != null && puedeEditar()) {
                editar(seleccionado);
            }
        });
    }

    private void configurarFiltroEstado() {

        cmbEstado.getItems().setAll(FiltroEstado.values());
        cmbEstado.setValue(FiltroEstado.ACTIVOS);

        cmbEstado.setConverter(new StringConverter<>() {

            @Override
            public String toString(FiltroEstado filtro) {
                if (filtro == null) {
                    return "";
                }
                return switch (filtro) {
                    case ACTIVOS -> "Activos";
                    case DADOS_DE_BAJA -> "Dados de baja";
                    case TODOS -> "Todos";
                };
            }

            @Override
            public FiltroEstado fromString(String texto) {
                return null;
            }
        });
    }

    private void configurarColumnas() {

        colCodigo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigo()));

        colCodigoBarras.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCodigoBarras()));

        colNombre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNombre()));

        colCategoria.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategoriaNombre()));

        colMarca.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMarcaNombre()));

        colPrecioCompra.setCellValueFactory(c -> precio(c.getValue(), ProductoDto::getPrecioCompra));

        colPrecioVenta.setCellValueFactory(c -> precio(c.getValue(), ProductoDto::getPrecioVenta));

        configurarColumnaStock();
        configurarColumnaEstado();

        // Las filas dadas de baja se ven atenuadas.
        tblProductos.setRowFactory(tabla -> new TableRow<>() {

            @Override
            protected void updateItem(ProductoDto producto, boolean empty) {
                super.updateItem(producto, empty);
                pseudoClassStateChanged(DADO_DE_BAJA,
                        !empty && producto != null && !producto.isActivo());
            }
        });

        configurarAcciones();
    }

    private SimpleStringProperty precio(ProductoDto producto, Function<ProductoDto, BigDecimal> getter) {
        return new SimpleStringProperty(CurrencyUtils.format(getter.apply(producto)));
    }

    /**
     * Muestra "stock / mínimo" y resalta los productos con stock bajo. Los que no
     * manejan stock muestran un guion.
     */
    private void configurarColumnaStock() {

        colStock.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));

        colStock.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(ProductoDto producto, boolean empty) {

                super.updateItem(producto, empty);

                setTooltip(null);

                if (empty || producto == null) {
                    setGraphic(null);
                    return;
                }

                if (!Boolean.TRUE.equals(producto.getManejaStock())) {
                    badge.setText("—");
                    badge.getStyleClass().setAll("label");
                    setTooltip(new Tooltip("No maneja stock"));
                    setGraphic(badge);
                    return;
                }

                badge.setText(valor(producto.getStock()) + " / mín. " + valor(producto.getStockMinimo()));

                if (producto.isStockBajo()) {
                    badge.getStyleClass().setAll("label", "estado-badge", "stock-bajo");
                    setTooltip(new Tooltip("Stock en el mínimo o por debajo"));
                } else {
                    badge.getStyleClass().setAll("label");
                }

                setGraphic(badge);
            }

            private String valor(Integer numero) {
                return numero == null ? "0" : numero.toString();
            }
        });
    }

    private void configurarColumnaEstado() {

        colEstado.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));

        colEstado.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(ProductoDto producto, boolean empty) {

                super.updateItem(producto, empty);

                if (empty || producto == null) {
                    setGraphic(null);
                    return;
                }

                badge.setText(producto.isActivo() ? "Activo" : "Dado de baja");
                badge.getStyleClass().setAll("label", "estado-badge",
                        producto.isActivo() ? "estado-activo" : "estado-inactivo");

                setGraphic(badge);
            }
        });
    }

    private void configurarAcciones() {

        List<Accion<ProductoDto>> acciones = new ArrayList<>();

        // Se puede editar también uno dado de baja, por ejemplo para asignarle
        // una categoría vigente antes de habilitarlo.
        if (puedeEditar()) {
            acciones.add(new Accion<>(FontAwesomeSolid.EDIT, "Editar",
                    "btn-icon-edit", producto -> true, this::editar));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.PRODUCTO_DAR_BAJA)) {
            acciones.add(new Accion<>(FontAwesomeSolid.BAN, "Dar de baja",
                    "btn-icon-delete", ProductoDto::isActivo, this::darDeBaja));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.PRODUCTO_HABILITAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.CHECK_CIRCLE, "Volver a habilitar",
                    "btn-icon-enable", producto -> !producto.isActivo(), this::habilitar));
        }

        colAcciones.setVisible(!acciones.isEmpty());
        colAcciones.setCellFactory(column -> new RowActionsTableCell<>(acciones));
    }

    private boolean puedeEditar() {
        return sesionUsuario.tienePermiso(PermisoCodigo.PRODUCTO_EDITAR);
    }

    /**
     * Carga la tabla respetando el texto de búsqueda y el estado elegidos, así
     * después de cada operación no se pierde el filtro aplicado.
     */
    private void cargarProductos() {

        String filtro = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();
        FiltroEstado estado = cmbEstado.getValue();

        List<ProductoDto> productos = productoService.buscar(filtro, estado);

        tblProductos.getItems().setAll(productos);

        lblSinResultados.setText(textoSinResultados(filtro, estado));

        lblTotal.setText("Total: " + productos.size()
                + (productos.size() == 1 ? " registro" : " registros"));

        long conStockBajo = productos.stream()
                .filter(p -> p.isActivo() && p.isStockBajo())
                .count();

        lblStockBajo.setText(conStockBajo == 1
                ? "1 producto con stock bajo"
                : conStockBajo + " productos con stock bajo");
        lblStockBajo.setVisible(conStockBajo > 0);
        lblStockBajo.setManaged(conStockBajo > 0);
    }

    private String textoSinResultados(String filtro, FiltroEstado estado) {

        if (!filtro.isEmpty()) {
            return "No se encontraron productos que coincidan con \"" + filtro + "\".";
        }

        return estado == FiltroEstado.DADOS_DE_BAJA
                ? "No hay productos dados de baja."
                : "No hay productos cargados.";
    }

    private void editarUnicoResultado() {

        if (tblProductos.getItems().size() == 1 && puedeEditar()) {
            editar(tblProductos.getItems().get(0));
        }
    }

    private void nuevo() {
        navigator.navigate(AppView.PRODUCTO_FORM);
    }

    private void editar(ProductoDto producto) {
        navigator.navigate(AppView.PRODUCTO_FORM, producto);
    }

    private void darDeBaja(ProductoDto producto) {

        boolean confirmado = Dialogs.confirmar(
                "Dar de baja producto",
                "¿Desea dar de baja el producto \"" + producto.getNombre() + "\"?\n\n"
                        + "Ya no aparecerá en el listado de activos ni se podrá usar en "
                        + "operaciones nuevas; las ventas y compras registradas se conservan. "
                        + "Se puede volver a habilitar más adelante.",
                "Dar de baja",
                true);

        if (!confirmado) {
            return;
        }

        execute(() -> {

            productoService.darDeBaja(producto.getId());

            cargarProductos();

            success("Producto dado de baja correctamente.");
        });
    }

    private void habilitar(ProductoDto producto) {

        boolean confirmado = Dialogs.confirmar(
                "Habilitar producto",
                "¿Desea volver a habilitar el producto \"" + producto.getNombre() + "\"?",
                "Habilitar",
                false);

        if (!confirmado) {
            return;
        }

        execute(() -> {

            productoService.habilitar(producto.getId());

            cargarProductos();

            success("Producto habilitado correctamente.");
        });
    }
}
