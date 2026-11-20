package org.gestionComercio.controller.compra;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.enums.EstadoCompra;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.CompraService;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.view.component.ComboBoxBusqueda;
import org.gestionComercio.view.component.SelectorFecha;
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

/**
 * Listado de compras. Se filtra por período, proveedor y estado en el
 * servicio; abre mostrando las compras del mes en curso. Desde la fila se ve
 * el detalle ({@link CompraDetalleDialog}), se marca como recibida una compra
 * pendiente (con COMPRA_CREAR) y se anula ({@link AnularCompraDialog}, con
 * COMPRA_ANULAR); las anuladas siguen en el listado.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class CompraListController extends AbstractController {

    /** Opción fija del combo de proveedores: no filtra. */
    private static final ProveedorDto TODOS = ProveedorDto.builder().razonSocial("Todos los proveedores").build();

    @FXML
    private Button btnNuevaCompra;

    @FXML
    private DatePicker dpDesde;

    @FXML
    private DatePicker dpHasta;

    @FXML
    private ComboBox<ProveedorDto> cmbProveedor;

    @FXML
    private ComboBox<EstadoCompra> cmbEstado;

    @FXML
    private Button btnLimpiar;

    @FXML
    private TableView<CompraDto> tblCompras;

    @FXML
    private TableColumn<CompraDto, String> colNumero;

    @FXML
    private TableColumn<CompraDto, String> colFecha;

    @FXML
    private TableColumn<CompraDto, String> colProveedor;

    @FXML
    private TableColumn<CompraDto, String> colFactura;

    @FXML
    private TableColumn<CompraDto, String> colUsuario;

    @FXML
    private TableColumn<CompraDto, String> colFormaPago;

    @FXML
    private TableColumn<CompraDto, String> colTotal;

    @FXML
    private TableColumn<CompraDto, EstadoCompra> colEstado;

    @FXML
    private TableColumn<CompraDto, Void> colAcciones;

    @FXML
    private Label lblSinResultados;

    @FXML
    private Label lblCantidad;

    @FXML
    private Label lblImporte;

    private final CompraService compraService;
    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;

    /** Evita consultar varias veces al cambiar varios filtros juntos. */
    private boolean actualizandoFiltros;

    @Override
    protected void initializeComponents() {

        actualizandoFiltros = true;

        SelectorFecha.configurar(dpDesde);
        SelectorFecha.configurar(dpHasta);
        configurarProveedor();
        configurarEstado();
        configurarColumnas();
        configurarAcciones();

        boolean puedeComprar = navigator.puedeAcceder(AppView.COMPRA_FORM);
        btnNuevaCompra.setVisible(puedeComprar);
        btnNuevaCompra.setManaged(puedeComprar);

        LocalDate hoy = LocalDate.now();
        dpDesde.setValue(hoy.withDayOfMonth(1));
        dpHasta.setValue(hoy);

        actualizandoFiltros = false;

        cargar();
    }

    @Override
    protected void initializeEvents() {

        dpDesde.valueProperty().addListener((obs, o, n) -> cargar());
        dpHasta.valueProperty().addListener((obs, o, n) -> cargar());
        cmbProveedor.valueProperty().addListener((obs, o, n) -> cargar());
        cmbEstado.valueProperty().addListener((obs, o, n) -> cargar());
        btnLimpiar.setOnAction(e -> limpiarFiltros());
        btnNuevaCompra.setOnAction(e -> navigator.navigate(AppView.COMPRA_FORM));
    }

    /** Combo con búsqueda por razón social; "Todos los proveedores" queda siempre visible. */
    private void configurarProveedor() {

        List<ProveedorDto> lista = new ArrayList<>();
        lista.add(TODOS);

        execute(() -> lista.addAll(compraService.listarProveedores()));

        ComboBoxBusqueda.configurar(cmbProveedor,
                        ProveedorDto::getRazonSocial,
                        p -> p == TODOS ? null : p.getRazonSocial() + " " + Objects.toString(p.getCuit(), ""))
                .setAll(lista);

        cmbProveedor.setValue(TODOS);
        cmbProveedor.setVisibleRowCount(12);
    }

    /** "Todos los estados" es el valor null del combo. */
    private void configurarEstado() {

        List<EstadoCompra> estados = new ArrayList<>();
        estados.add(null);
        estados.addAll(List.of(EstadoCompra.values()));
        cmbEstado.getItems().setAll(estados);

        cmbEstado.setCellFactory(lista -> new CeldaEstado());
        cmbEstado.setButtonCell(new CeldaEstado());
        cmbEstado.setValue(null);
    }

    private static final class CeldaEstado extends ListCell<EstadoCompra> {

        @Override
        protected void updateItem(EstadoCompra estado, boolean empty) {
            super.updateItem(estado, empty);
            setText(estado == null ? "Todos los estados" : estado.getDescripcion());
        }
    }

    private void configurarColumnas() {

        colNumero.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getId())));
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.format(c.getValue().getFecha())));
        colProveedor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProveedorNombre()));
        colFactura.setCellValueFactory(c -> new SimpleStringProperty(Objects.toString(c.getValue().getNumeroFactura(), "")));
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(
                CompraFormato.usuario(c.getValue().getUsuarioNombreCompleto(), c.getValue().getUsuarioNombre())));
        colFormaPago.setCellValueFactory(c -> new SimpleStringProperty(Objects.toString(c.getValue().getFormaPago(), "")));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(CompraFormato.importe(c.getValue().getTotal())));

        colEstado.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getEstadoCompra()));
        colEstado.setCellFactory(column -> new TableCell<>() {

            @Override
            protected void updateItem(EstadoCompra estado, boolean empty) {
                super.updateItem(estado, empty);
                setGraphic(empty || estado == null ? null : CompraFormato.badgeEstado(estado));
            }
        });
    }

    /**
     * Ver detalle desde el ícono de la fila o con doble clic. Recibir, sólo
     * con COMPRA_CREAR y en compras pendientes; anular, sólo con COMPRA_ANULAR
     * y en compras que todavía se pueden anular.
     */
    private void configurarAcciones() {

        List<Accion<CompraDto>> acciones = new ArrayList<>();

        acciones.add(new Accion<>(FontAwesomeSolid.LIST, "Ver detalle", "btn-icon-edit",
                compra -> true, this::verDetalle));

        if (sesionUsuario.tienePermiso(PermisoCodigo.COMPRA_CREAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.TRUCK, "Marcar como recibida", "btn-icon-edit",
                    compra -> CompraFormato.recibible(compra.getEstadoCompra()), this::recibir));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.COMPRA_ANULAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.BAN, "Anular", "btn-icon-delete",
                    compra -> CompraFormato.anulable(compra.getEstadoCompra()), this::anular));
        }

        colAcciones.setCellFactory(column -> new RowActionsTableCell<>(acciones));

        tblCompras.setRowFactory(tabla -> {

            TableRow<CompraDto> fila = new TableRow<>();

            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) {
                    verDetalle(fila.getItem());
                }
            });

            return fila;
        });
    }

    private void verDetalle(CompraDto compra) {
        execute(() -> new CompraDetalleDialog(compraService.obtener(compra.getId())).mostrar());
    }

    private void recibir(CompraDto compra) {

        if (!confirm("Recibir compra", "¿Marcar la " + CompraFormato.titulo(compra).toLowerCase()
                + " de " + compra.getProveedorNombre() + " como recibida?\n\n"
                + "Se sumará al stock la mercadería de la compra.")) {
            return;
        }

        execute(() -> {
            CompraDto recibida = compraService.recibir(compra.getId());
            cargar();
            success(CompraFormato.titulo(recibida) + " recibida: se sumó su mercadería al stock.");
        });
    }

    private void anular(CompraDto compra) {

        new AnularCompraDialog(compraService, compra).mostrar().ifPresent(anulada -> {
            cargar();
            success(CompraFormato.titulo(anulada) + " anulada correctamente.");
        });
    }

    private void limpiarFiltros() {

        actualizandoFiltros = true;
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        cmbProveedor.setValue(TODOS);
        cmbEstado.setValue(null);
        actualizandoFiltros = false;

        cargar();
    }

    private void cargar() {

        if (actualizandoFiltros) {
            return;
        }

        ProveedorDto proveedor = cmbProveedor.getValue();
        Long proveedorId = proveedor == null || proveedor == TODOS ? null : proveedor.getId();

        execute(() -> mostrar(compraService.listar(
                dpDesde.getValue(), dpHasta.getValue(), cmbEstado.getValue(), proveedorId)));
    }

    private void mostrar(List<CompraDto> compras) {

        tblCompras.getItems().setAll(compras);

        boolean filtrado = dpDesde.getValue() != null || dpHasta.getValue() != null
                || (cmbProveedor.getValue() != null && cmbProveedor.getValue() != TODOS)
                || cmbEstado.getValue() != null;
        lblSinResultados.setText(filtrado ? "No hay compras que coincidan con los filtros." : "No hay compras.");

        lblCantidad.setText("Total: " + compras.size() + (compras.size() == 1 ? " compra" : " compras"));

        // Las compras anuladas o canceladas no suman al importe comprado.
        BigDecimal importe = compras.stream()
                .filter(c -> CompraFormato.sumaAlImporte(c.getEstadoCompra()))
                .map(CompraDto::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblImporte.setText(compras.isEmpty() ? "" : "Importe comprado: " + CompraFormato.importe(importe));
    }
}
