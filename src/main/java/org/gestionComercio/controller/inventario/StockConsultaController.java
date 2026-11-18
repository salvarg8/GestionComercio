package org.gestionComercio.controller.inventario;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.categoria.CategoriaDto;
import org.gestionComercio.dto.inventario.ResumenStock;
import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.enums.FiltroStock;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.SituacionStock;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.CategoriaService;
import org.gestionComercio.service.ConsultaStockService;
import org.gestionComercio.service.InventarioService;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.view.component.table.RowActionsTableCell;
import org.gestionComercio.view.component.table.RowActionsTableCell.Accion;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Consulta de las existencias actuales de los productos activos: por producto
 * (búsqueda por nombre o código), por categoría y por situación (stock bajo,
 * sin stock). Según los permisos del usuario permite además registrar
 * entradas y salidas ({@link MovimientoStockDialog}) y corregir el stock
 * indicando el stock real ({@link AjusteStockDialog}). Desde cada producto se
 * puede abrir su historial de movimientos.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class StockConsultaController extends AbstractController {

    /** Opción "todas las categorías" del combo (id null = sin filtro). */
    private static final CategoriaDto TODAS_LAS_CATEGORIAS =
            CategoriaDto.builder().nombre("Todas las categorías").build();

    @FXML
    private ComboBox<CategoriaDto> cmbCategoria;

    @FXML
    private ComboBox<FiltroStock> cmbSituacion;

    @FXML
    private TextField txtBuscar;

    @FXML
    private Label lblResumenTotal;

    @FXML
    private Label lblResumenStockBajo;

    @FXML
    private Label lblResumenSinStock;

    @FXML
    private TableView<StockProductoDto> tblStock;

    @FXML
    private TableColumn<StockProductoDto, String> colCodigo;

    @FXML
    private TableColumn<StockProductoDto, String> colNombre;

    @FXML
    private TableColumn<StockProductoDto, String> colCategoria;

    @FXML
    private TableColumn<StockProductoDto, String> colMarca;

    @FXML
    private TableColumn<StockProductoDto, String> colUnidad;

    @FXML
    private TableColumn<StockProductoDto, String> colStock;

    @FXML
    private TableColumn<StockProductoDto, String> colStockMinimo;

    @FXML
    private TableColumn<StockProductoDto, SituacionStock> colSituacion;

    @FXML
    private TableColumn<StockProductoDto, Void> colAcciones;

    @FXML
    private Label lblSinResultados;

    @FXML
    private Label lblTotal;

    private final ConsultaStockService consultaStockService;
    private final CategoriaService categoriaService;
    private final InventarioService inventarioService;
    private final SesionUsuario sesionUsuario;
    private final Navigator navigator;

    @Override
    protected void initializeComponents() {

        configurarFiltroCategoria();
        configurarFiltroSituacion();
        configurarColumnas();
        configurarAcciones();
        cargar();
        txtBuscar.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        txtBuscar.textProperty().addListener((obs, oldValue, newValue) -> cargar());
        cmbCategoria.valueProperty().addListener((obs, oldValue, newValue) -> cargar());
        cmbSituacion.valueProperty().addListener((obs, oldValue, newValue) -> cargar());

        // Los contadores se pueden clicar para filtrar por esa situación.
        for (Label contador : List.of(lblResumenTotal, lblResumenStockBajo, lblResumenSinStock)) {
            contador.setCursor(Cursor.HAND);
        }

        lblResumenTotal.setOnMouseClicked(e -> cmbSituacion.setValue(FiltroStock.TODOS));
        lblResumenStockBajo.setOnMouseClicked(e -> cmbSituacion.setValue(FiltroStock.STOCK_BAJO));
        lblResumenSinStock.setOnMouseClicked(e -> cmbSituacion.setValue(FiltroStock.SIN_STOCK));
    }

    private void configurarFiltroCategoria() {

        List<CategoriaDto> categorias = new ArrayList<>();
        categorias.add(TODAS_LAS_CATEGORIAS);
        categorias.addAll(categoriaService.findParaSeleccion());

        cmbCategoria.getItems().setAll(categorias);
        cmbCategoria.setValue(TODAS_LAS_CATEGORIAS);

        cmbCategoria.setConverter(new StringConverter<>() {

            @Override
            public String toString(CategoriaDto categoria) {
                return categoria == null ? "" : categoria.getNombre();
            }

            @Override
            public CategoriaDto fromString(String texto) {
                return null;
            }
        });
    }

    private void configurarFiltroSituacion() {

        cmbSituacion.getItems().setAll(FiltroStock.values());
        cmbSituacion.setValue(FiltroStock.TODOS);
    }

    private void configurarColumnas() {

        colCodigo.setCellValueFactory(c -> texto(c.getValue(), StockProductoDto::codigo));

        colNombre.setCellValueFactory(c -> texto(c.getValue(), StockProductoDto::nombre));

        colCategoria.setCellValueFactory(c -> texto(c.getValue(), StockProductoDto::categoriaNombre));

        colMarca.setCellValueFactory(c -> texto(c.getValue(), StockProductoDto::marcaNombre));

        colUnidad.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().unidadMedida() == null ? "" : c.getValue().unidadMedida().getDescripcion()));

        colStock.setCellValueFactory(c -> cantidad(c.getValue(), StockProductoDto::stock));

        colStockMinimo.setCellValueFactory(c -> cantidad(c.getValue(), StockProductoDto::stockMinimo));

        configurarColumnaSituacion();
    }

    private SimpleStringProperty texto(StockProductoDto stock, Function<StockProductoDto, String> getter) {
        return new SimpleStringProperty(getter.apply(stock));
    }

    /** Los productos que no manejan stock muestran un guion en lugar de cantidades. */
    private SimpleStringProperty cantidad(StockProductoDto stock, Function<StockProductoDto, BigDecimal> getter) {

        if (!Boolean.TRUE.equals(stock.manejaStock())) {
            return new SimpleStringProperty("—");
        }

        return new SimpleStringProperty(CantidadUtils.formatear(getter.apply(stock)));
    }

    private void configurarColumnaSituacion() {

        colSituacion.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().situacion()));

        colSituacion.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(SituacionStock situacion, boolean empty) {

                super.updateItem(situacion, empty);

                if (empty || situacion == null) {
                    setGraphic(null);
                    return;
                }

                badge.setText(situacion.getDescripcion());
                badge.getStyleClass().setAll("label", "estado-badge", estilo(situacion));

                setGraphic(badge);
            }
        });
    }

    /**
     * Ver movimientos está siempre disponible (la pantalla ya exige INVENTARIO_VER).
     * Entrada, salida y ajuste sólo aparecen con su permiso y en productos que
     * manejan stock.
     */
    private void configurarAcciones() {

        boolean puedeAjustar = puedeAjustar();

        List<Accion<StockProductoDto>> acciones = new ArrayList<>();

        acciones.add(new Accion<StockProductoDto>(FontAwesomeSolid.HISTORY, "Ver movimientos", "btn-icon-edit",
                stock -> true, this::verMovimientos));

        if (sesionUsuario.tienePermiso(PermisoCodigo.INVENTARIO_REGISTRAR_ENTRADA)) {
            acciones.add(new Accion<StockProductoDto>(FontAwesomeSolid.PLUS_CIRCLE, "Registrar entrada",
                    "btn-icon-enable", stock -> Boolean.TRUE.equals(stock.manejaStock()),
                    stock -> registrarMovimiento(stock, MovimientoStockDialog.Operacion.ENTRADA)));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.INVENTARIO_REGISTRAR_SALIDA)) {
            acciones.add(new Accion<StockProductoDto>(FontAwesomeSolid.MINUS_CIRCLE, "Registrar salida",
                    "btn-icon-delete", stock -> Boolean.TRUE.equals(stock.manejaStock()),
                    stock -> registrarMovimiento(stock, MovimientoStockDialog.Operacion.SALIDA)));
        }

        if (puedeAjustar) {
            acciones.add(new Accion<StockProductoDto>(FontAwesomeSolid.SLIDERS_H, "Ajustar stock", "btn-icon-edit",
                    stock -> Boolean.TRUE.equals(stock.manejaStock()), this::ajustar));
        }

        colAcciones.setCellFactory(column -> new RowActionsTableCell<>(acciones));

        if (!puedeAjustar) {
            return;
        }

        // Doble clic sobre un producto: atajo para ajustarlo.
        tblStock.setRowFactory(tabla -> {

            TableRow<StockProductoDto> fila = new TableRow<>();

            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()
                        && Boolean.TRUE.equals(fila.getItem().manejaStock())) {
                    ajustar(fila.getItem());
                }
            });

            return fila;
        });
    }

    private boolean puedeAjustar() {
        return sesionUsuario.tienePermiso(PermisoCodigo.INVENTARIO_AJUSTAR);
    }

    /** Abre el historial de movimientos filtrado por el producto. */
    private void verMovimientos(StockProductoDto stock) {
        navigator.navigate(AppView.MOVIMIENTO_HISTORIAL, stock.productoId());
    }

    /** Abre el ajuste del producto; si se registra, informa el resultado y refresca la tabla. */
    private void ajustar(StockProductoDto stock) {

        Optional<MovimientoInventarioDto> movimiento =
                new AjusteStockDialog(inventarioService, stock).mostrar();

        movimiento.ifPresent(m -> {

            cargar();

            success("Se ajustó el stock de \"" + stock.nombre() + "\".\n\n"
                    + "Stock anterior: " + CantidadUtils.formatear(m.getStockAnterior()) + "\n"
                    + "Diferencia: " + (m.getCantidad().signum() > 0 ? "+" : "")
                    + CantidadUtils.formatear(m.getCantidad()) + "\n"
                    + "Stock resultante: " + CantidadUtils.formatear(m.getStockResultante()) + "\n"
                    + "Motivo: " + m.getMotivoAjuste().getDescripcion()
                    + (m.getMotivo().equals(m.getMotivoAjuste().getDescripcion()) ? "" : " — " + m.getMotivo()));
        });
    }

    /** Abre la entrada o salida del producto; si se registra, informa el resultado y refresca la tabla. */
    private void registrarMovimiento(StockProductoDto stock, MovimientoStockDialog.Operacion operacion) {

        Optional<MovimientoInventarioDto> movimiento =
                new MovimientoStockDialog(inventarioService, stock, operacion).mostrar();

        movimiento.ifPresent(m -> {

            cargar();

            success("Se registró la " + (operacion == MovimientoStockDialog.Operacion.ENTRADA ? "entrada" : "salida")
                    + " de \"" + stock.nombre() + "\".\n\n"
                    + "Stock anterior: " + CantidadUtils.formatear(m.getStockAnterior()) + "\n"
                    + "Cantidad: " + (m.getCantidad().signum() > 0 ? "+" : "")
                    + CantidadUtils.formatear(m.getCantidad()) + "\n"
                    + "Stock resultante: " + CantidadUtils.formatear(m.getStockResultante()) + "\n"
                    + "Motivo: " + m.getMotivo());
        });
    }

    private static String estilo(SituacionStock situacion) {

        return switch (situacion) {
            case SIN_STOCK -> "sin-stock";
            case STOCK_BAJO -> "stock-bajo";
            case NORMAL -> "estado-activo";
            case NO_MANEJA_STOCK -> "estado-inactivo";
        };
    }

    /**
     * Recarga la tabla con los filtros elegidos y actualiza los contadores
     * (que no dependen de los filtros: siempre cuentan todos los productos activos).
     */
    private void cargar() {

        execute(() -> {

            String texto = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();
            CategoriaDto categoria = cmbCategoria.getValue();
            Long categoriaId = categoria == null ? null : categoria.getId();
            FiltroStock filtro = cmbSituacion.getValue();

            List<StockProductoDto> productos = consultaStockService.consultar(texto, categoriaId, filtro);

            tblStock.getItems().setAll(productos);

            lblSinResultados.setText(textoSinResultados(texto, categoriaId, filtro));

            lblTotal.setText("Total: " + productos.size()
                    + (productos.size() == 1 ? " registro" : " registros"));

            mostrarResumen(consultaStockService.resumen());
        });
    }

    private void mostrarResumen(ResumenStock resumen) {

        lblResumenTotal.setText(cantidadProductos(resumen.productosConStock()) + " con control de stock");
        lblResumenStockBajo.setText(resumen.conStockBajo() + " con stock bajo");
        lblResumenSinStock.setText(resumen.sinStock() + " sin stock");
    }

    private static String cantidadProductos(long cantidad) {
        return cantidad == 1 ? "1 producto" : cantidad + " productos";
    }

    private String textoSinResultados(String texto, Long categoriaId, FiltroStock filtro) {

        if (!texto.isEmpty()) {
            return "No se encontraron productos que coincidan con \"" + texto + "\".";
        }

        if (filtro == FiltroStock.SIN_STOCK) {
            return "No hay productos sin stock" + (categoriaId != null ? " en esta categoría." : ".");
        }

        if (filtro == FiltroStock.STOCK_BAJO) {
            return "No hay productos con stock bajo" + (categoriaId != null ? " en esta categoría." : ".");
        }

        return categoriaId != null
                ? "No hay productos activos en esta categoría."
                : "No hay productos cargados.";
    }
}
