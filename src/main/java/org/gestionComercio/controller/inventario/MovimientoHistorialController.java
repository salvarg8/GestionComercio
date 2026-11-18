package org.gestionComercio.controller.inventario;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.inventario.FiltroMovimientos;
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.service.ConsultaStockService;
import org.gestionComercio.service.InventarioService;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.view.component.ComboBoxBusqueda;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Historial de movimientos de inventario, filtrable por producto, tipo y rango
 * de fechas. Cada movimiento muestra la cantidad, el stock anterior y el
 * resultante, el motivo y el usuario responsable.
 *
 * <p>Al filtrar por un producto se informa su stock actual y se puede ver el
 * historial en orden cronológico, para seguir la evolución de su stock
 * movimiento a movimiento.
 *
 * <p>Recibe opcionalmente el ID de un producto ({@link DataReceiver}) para
 * abrir con su historial ya filtrado, por ejemplo desde la consulta de stock.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class MovimientoHistorialController extends AbstractController implements DataReceiver<Long> {

    /** Opción "todos los productos" del combo (productoId null = sin filtro). */
    private static final StockProductoDto TODOS_LOS_PRODUCTOS = new StockProductoDto(
            null, null, null, "Todos los productos", null, null, null, null, null, null, null, null);

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private ComboBox<StockProductoDto> cmbProducto;

    @FXML
    private ComboBox<TipoMovimientoInventario> cmbTipo;

    @FXML
    private DatePicker dpDesde;

    @FXML
    private DatePicker dpHasta;

    @FXML
    private Button btnLimpiar;

    @FXML
    private CheckBox chkCronologico;

    @FXML
    private Label lblStockActual;

    @FXML
    private TableView<MovimientoInventarioDto> tblMovimientos;

    @FXML
    private TableColumn<MovimientoInventarioDto, String> colFecha;

    @FXML
    private TableColumn<MovimientoInventarioDto, String> colProducto;

    @FXML
    private TableColumn<MovimientoInventarioDto, TipoMovimientoInventario> colTipo;

    @FXML
    private TableColumn<MovimientoInventarioDto, String> colCantidad;

    @FXML
    private TableColumn<MovimientoInventarioDto, String> colStockAnterior;

    @FXML
    private TableColumn<MovimientoInventarioDto, String> colStockResultante;

    @FXML
    private TableColumn<MovimientoInventarioDto, String> colMotivo;

    @FXML
    private TableColumn<MovimientoInventarioDto, String> colOrigen;

    @FXML
    private TableColumn<MovimientoInventarioDto, String> colUsuario;

    @FXML
    private Label lblSinResultados;

    @FXML
    private Label lblTotal;

    private final InventarioService inventarioService;
    private final ConsultaStockService consultaStockService;

    /** Productos del combo (sin filtrar por la búsqueda). */
    private ObservableList<StockProductoDto> productos;

    /** Evita consultar varias veces mientras se cambian varios filtros juntos. */
    private boolean actualizandoFiltros;

    @Override
    protected void initializeComponents() {

        configurarFiltroProducto();
        configurarFiltroTipo();
        configurarFecha(dpDesde);
        configurarFecha(dpHasta);
        configurarColumnas();
        cargar();
    }

    @Override
    protected void initializeEvents() {

        cmbProducto.valueProperty().addListener((obs, oldValue, newValue) -> cargar());
        cmbTipo.valueProperty().addListener((obs, oldValue, newValue) -> cargar());
        dpDesde.valueProperty().addListener((obs, oldValue, newValue) -> cargar());
        dpHasta.valueProperty().addListener((obs, oldValue, newValue) -> cargar());
        chkCronologico.selectedProperty().addListener((obs, oldValue, newValue) -> cargar());

        btnLimpiar.setOnAction(e -> limpiarFiltros());
    }

    /**
     * Abre el historial filtrado por el producto indicado, en orden cronológico.
     * Si el producto todavía no tiene movimientos se agrega igual al combo, para
     * que la tabla vacía refleje su historial y no el de todos los productos.
     */
    @Override
    public void setData(Long productoId) {

        execute(() -> {

            StockProductoDto producto = productos.stream()
                    .filter(p -> Objects.equals(p.productoId(), productoId))
                    .findFirst()
                    .orElseGet(() -> {
                        StockProductoDto nuevo = consultaStockService.consultarProducto(productoId);
                        productos.add(nuevo);
                        return nuevo;
                    });

            actualizandoFiltros = true;
            cmbProducto.setValue(producto);
            chkCronologico.setSelected(true);
            actualizandoFiltros = false;

            cargar();
        });
    }

    /**
     * Combo con búsqueda por código, nombre o código de barras. "Todos los
     * productos" queda siempre visible en la lista.
     */
    private void configurarFiltroProducto() {

        productos = ComboBoxBusqueda.configurar(cmbProducto,
                MovimientoHistorialController::textoProducto,
                p -> p.productoId() == null ? null
                        : p.codigo() + " " + p.nombre() + " " + Objects.toString(p.codigoBarras(), ""));

        List<StockProductoDto> lista = new ArrayList<>();
        lista.add(TODOS_LOS_PRODUCTOS);

        execute(() -> lista.addAll(consultaStockService.consultarProductosConMovimientos()));

        productos.setAll(lista);
        cmbProducto.setValue(TODOS_LOS_PRODUCTOS);
        cmbProducto.setVisibleRowCount(15);
        cmbProducto.setPromptText("Buscar producto por código, nombre o código de barras...");
    }

    private static String textoProducto(StockProductoDto producto) {

        if (producto.productoId() == null) {
            return producto.nombre();
        }

        return producto.codigo() + " - " + producto.nombre()
                + (producto.isActivo() ? "" : " (dado de baja)");
    }

    private void configurarFiltroTipo() {

        cmbTipo.getItems().add(null);
        cmbTipo.getItems().addAll(TipoMovimientoInventario.values());
        cmbTipo.setValue(null);

        // El null del combo es "todos los tipos".
        cmbTipo.setConverter(new StringConverter<>() {

            @Override
            public String toString(TipoMovimientoInventario tipo) {
                return tipo == null ? "Todos los tipos" : tipo.getDescripcion();
            }

            @Override
            public TipoMovimientoInventario fromString(String texto) {
                return null;
            }
        });

        cmbTipo.setButtonCell(new ListCell<>() {

            @Override
            protected void updateItem(TipoMovimientoInventario tipo, boolean empty) {
                super.updateItem(tipo, empty);
                setText(empty ? null : cmbTipo.getConverter().toString(tipo));
            }
        });
    }

    /** Formato dd/MM/yyyy; un texto que no es una fecha válida deja el campo vacío. */
    private void configurarFecha(DatePicker datePicker) {

        datePicker.setPromptText("dd/mm/aaaa");

        datePicker.setConverter(new StringConverter<>() {

            @Override
            public String toString(LocalDate fecha) {
                return fecha == null ? "" : fecha.format(FORMATO_FECHA);
            }

            @Override
            public LocalDate fromString(String texto) {

                if (texto == null || texto.isBlank()) {
                    return null;
                }

                try {
                    return LocalDate.parse(texto.trim(), FORMATO_FECHA);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
    }

    private void configurarColumnas() {

        colFecha.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.format(c.getValue().getFecha())));

        colProducto.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProductoCodigo() + " - " + c.getValue().getProductoNombre()));

        colCantidad.setCellValueFactory(c -> new SimpleStringProperty(cantidadConSigno(c.getValue().getCantidad())));

        colStockAnterior.setCellValueFactory(c -> new SimpleStringProperty(
                CantidadUtils.formatear(c.getValue().getStockAnterior())));

        colStockResultante.setCellValueFactory(c -> new SimpleStringProperty(
                CantidadUtils.formatear(c.getValue().getStockResultante())));

        colMotivo.setCellValueFactory(c -> new SimpleStringProperty(motivo(c.getValue())));

        colOrigen.setCellValueFactory(c -> new SimpleStringProperty(origen(c.getValue())));

        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(usuario(c.getValue())));

        configurarColumnaTipo();
    }

    private void configurarColumnaTipo() {

        colTipo.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTipo()));

        colTipo.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(TipoMovimientoInventario tipo, boolean empty) {

                super.updateItem(tipo, empty);

                if (empty || tipo == null) {
                    setGraphic(null);
                    return;
                }

                badge.setText(tipo.getDescripcion());
                badge.getStyleClass().setAll("label", "estado-badge", estilo(tipo));

                setGraphic(badge);
            }
        });
    }

    private static String estilo(TipoMovimientoInventario tipo) {

        return switch (tipo) {
            case ENTRADA -> "estado-activo";
            case SALIDA -> "stock-bajo";
            case AJUSTE -> "movimiento-ajuste";
        };
    }

    /** Las entradas se muestran con "+" para distinguirlas de las salidas. */
    private static String cantidadConSigno(BigDecimal cantidad) {

        if (cantidad == null) {
            return "";
        }

        return (cantidad.signum() > 0 ? "+" : "") + CantidadUtils.formatear(cantidad);
    }

    /** En los ajustes antepone la causa, salvo que el motivo sea la causa misma. */
    private static String motivo(MovimientoInventarioDto movimiento) {

        String motivo = movimiento.getMotivo() == null ? "" : movimiento.getMotivo();

        if (movimiento.getMotivoAjuste() == null) {
            return motivo;
        }

        String causa = movimiento.getMotivoAjuste().getDescripcion();

        return motivo.equals(causa) ? causa : causa + " — " + motivo;
    }

    /** Manual, o el comprobante que lo generó; las reversiones por anulación se aclaran. */
    private static String origen(MovimientoInventarioDto movimiento) {

        if (movimiento.getOrigen() == null) {
            return "";
        }

        String origen = movimiento.getOrigen().getDescripcion()
                + (movimiento.getOrigenId() == null ? "" : " #" + movimiento.getOrigenId());

        return movimiento.getMovimientoRevertidoId() == null ? origen : origen + " (anulación)";
    }

    private static String usuario(MovimientoInventarioDto movimiento) {

        String nombreCompleto = movimiento.getUsuarioNombreCompleto() == null
                ? "" : movimiento.getUsuarioNombreCompleto().trim();

        if (nombreCompleto.isEmpty()) {
            return Objects.toString(movimiento.getUsuarioNombre(), "");
        }

        return nombreCompleto + " (" + movimiento.getUsuarioNombre() + ")";
    }

    private void limpiarFiltros() {

        actualizandoFiltros = true;
        cmbProducto.setValue(TODOS_LOS_PRODUCTOS);
        cmbTipo.setValue(null);
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        chkCronologico.setSelected(false);
        actualizandoFiltros = false;

        cargar();
    }

    private FiltroMovimientos filtro() {

        StockProductoDto producto = cmbProducto.getValue();

        return new FiltroMovimientos(
                producto == null ? null : producto.productoId(),
                cmbTipo.getValue(),
                dpDesde.getValue(),
                dpHasta.getValue());
    }

    /** Recarga la tabla con los filtros elegidos. */
    private void cargar() {

        if (actualizandoFiltros) {
            return;
        }

        FiltroMovimientos filtro = filtro();

        if (filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta())) {
            tblMovimientos.getItems().clear();
            lblSinResultados.setText("La fecha desde no puede ser posterior a la fecha hasta.");
            lblTotal.setText("Total: 0 movimientos");
            return;
        }

        execute(() -> {

            List<MovimientoInventarioDto> movimientos = new ArrayList<>(inventarioService.consultarMovimientos(filtro));

            // El servicio los devuelve del más reciente al más antiguo.
            if (chkCronologico.isSelected()) {
                Collections.reverse(movimientos);
            }

            tblMovimientos.getItems().setAll(movimientos);

            lblSinResultados.setText(textoSinResultados(filtro));

            lblTotal.setText("Total: " + movimientos.size()
                    + (movimientos.size() == 1 ? " movimiento" : " movimientos"));

            mostrarStockActual(filtro.productoId());
        });
    }

    /** Stock actual del producto filtrado: es el resultante de su último movimiento. */
    private void mostrarStockActual(Long productoId) {

        if (productoId == null) {
            lblStockActual.setVisible(false);
            lblStockActual.setManaged(false);
            return;
        }

        StockProductoDto stock = consultaStockService.consultarProducto(productoId);

        lblStockActual.setText("Stock actual de \"" + stock.nombre() + "\": "
                + CantidadUtils.formatear(stock.stock()));
        lblStockActual.setVisible(true);
        lblStockActual.setManaged(true);
    }

    private static String textoSinResultados(FiltroMovimientos filtro) {

        boolean hayFiltros = filtro.productoId() != null || filtro.tipo() != null
                || filtro.desde() != null || filtro.hasta() != null;

        return hayFiltros
                ? "No hay movimientos que coincidan con los filtros."
                : "No hay movimientos registrados.";
    }
}
