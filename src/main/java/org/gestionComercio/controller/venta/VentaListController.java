package org.gestionComercio.controller.venta;

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
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.facturacion.AutorizacionComprobantes;
import org.gestionComercio.controller.facturacion.FacturaElectronicaFormato;
import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.FacturacionElectronicaService;
import org.gestionComercio.service.VentaService;
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
 * Listado de ventas de todos los vendedores. Se filtra por período y estado
 * en el servicio, y por cliente sobre lo ya consultado (por nombre, sin
 * distinguir mayúsculas ni acentos). Abre mostrando las ventas del día.
 * Desde la fila se ve el detalle ({@link VentaDetalleDialog}), se imprime o
 * reimprime el comprobante ({@link ImpresionComprobante}) y se anula la
 * venta ({@link AnularVentaDialog}); las anuladas siguen en el listado.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class VentaListController extends AbstractController {

    @FXML
    private Button btnPendientesCae;

    @FXML
    private Button btnNuevaVenta;

    @FXML
    private DatePicker dpDesde;

    @FXML
    private DatePicker dpHasta;

    @FXML
    private TextField txtCliente;

    @FXML
    private ComboBox<EstadoVenta> cmbEstado;

    @FXML
    private Button btnLimpiar;

    @FXML
    private TableView<VentaDto> tblVentas;

    @FXML
    private TableColumn<VentaDto, String> colNumero;

    @FXML
    private TableColumn<VentaDto, String> colFecha;

    @FXML
    private TableColumn<VentaDto, String> colCliente;

    @FXML
    private TableColumn<VentaDto, String> colVendedor;

    @FXML
    private TableColumn<VentaDto, String> colFormaPago;

    @FXML
    private TableColumn<VentaDto, String> colTotal;

    @FXML
    private TableColumn<VentaDto, EstadoVenta> colEstado;

    @FXML
    private TableColumn<VentaDto, VentaDto> colFactura;

    @FXML
    private TableColumn<VentaDto, Void> colAcciones;

    @FXML
    private Label lblSinResultados;

    @FXML
    private Label lblCantidad;

    @FXML
    private Label lblImporte;

    private final VentaService ventaService;
    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;
    private final ImpresionComprobante impresionComprobante;
    private final FacturacionElectronicaService facturacion;
    private final AutorizacionComprobantes autorizacion;

    /** Ventas del período y estado elegidos, antes de filtrar por cliente. */
    private List<VentaDto> consultadas = List.of();

    /** Evita consultar varias veces al cambiar varios filtros juntos. */
    private boolean actualizandoFiltros;

    @Override
    protected void initializeComponents() {

        SelectorFecha.configurar(dpDesde);
        SelectorFecha.configurar(dpHasta);
        configurarEstado();
        configurarColumnas();
        configurarAcciones();

        boolean puedeVender = navigator.puedeAcceder(AppView.PUNTO_VENTA);
        btnNuevaVenta.setVisible(puedeVender);
        btnNuevaVenta.setManaged(puedeVender);

        actualizandoFiltros = true;
        dpDesde.setValue(LocalDate.now());
        dpHasta.setValue(LocalDate.now());
        actualizandoFiltros = false;

        cargar();
    }

    @Override
    protected void initializeEvents() {

        dpDesde.valueProperty().addListener((obs, o, n) -> cargar());
        dpHasta.valueProperty().addListener((obs, o, n) -> cargar());
        cmbEstado.valueProperty().addListener((obs, o, n) -> cargar());
        txtCliente.textProperty().addListener((obs, o, n) -> filtrarPorCliente());
        btnLimpiar.setOnAction(e -> limpiarFiltros());
        btnNuevaVenta.setOnAction(e -> navigator.navigate(AppView.PUNTO_VENTA));
        btnPendientesCae.setOnAction(e -> navigator.navigate(AppView.FACTURACION_PENDIENTES));
    }

    /** "Todos los estados" es el valor null del combo. */
    private void configurarEstado() {

        List<EstadoVenta> estados = new ArrayList<>();
        estados.add(null);
        estados.addAll(List.of(EstadoVenta.values()));
        cmbEstado.getItems().setAll(estados);

        cmbEstado.setCellFactory(lista -> new CeldaEstado());
        cmbEstado.setButtonCell(new CeldaEstado());
        cmbEstado.setValue(null);
    }

    private static final class CeldaEstado extends ListCell<EstadoVenta> {

        @Override
        protected void updateItem(EstadoVenta estado, boolean empty) {
            super.updateItem(estado, empty);
            setText(estado == null ? "Todos los estados" : estado.getDescripcion());
        }
    }

    private void configurarColumnas() {

        colNumero.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getComprobante()));
        colFecha.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.format(c.getValue().getFecha())));
        colCliente.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClienteNombre()));
        colVendedor.setCellValueFactory(c -> new SimpleStringProperty(
                VentaFormato.usuario(c.getValue().getUsuarioNombreCompleto(), c.getValue().getUsuarioNombre())));
        colFormaPago.setCellValueFactory(c -> new SimpleStringProperty(Objects.toString(c.getValue().getFormaPago(), "")));
        colTotal.setCellValueFactory(c -> new SimpleStringProperty(VentaFormato.importe(c.getValue().getTotal())));

        colEstado.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getEstadoVenta()));
        colEstado.setCellFactory(column -> new TableCell<>() {

            @Override
            protected void updateItem(EstadoVenta estado, boolean empty) {
                super.updateItem(estado, empty);
                setGraphic(empty || estado == null ? null : VentaFormato.badgeEstado(estado));
            }
        });

        // Facturada / pendiente de CAE / rechazada; vacía en tickets y remitos.
        colFactura.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colFactura.setCellFactory(column -> new TableCell<>() {

            @Override
            protected void updateItem(VentaDto venta, boolean empty) {

                super.updateItem(venta, empty);

                Label badge = empty ? null : FacturaElectronicaFormato.badgeVenta(venta);

                if (badge != null) {
                    ComprobanteArcaDto comprobante = FacturaElectronicaFormato.comprobanteDeReferencia(venta);
                    if (comprobante.getObservaciones() != null && comprobante.getEstado().faltaAutorizar()) {
                        badge.setTooltip(new Tooltip(comprobante.getObservaciones()));
                    }
                }

                setGraphic(badge);
            }
        });
    }

    /**
     * Ver detalle desde el ícono de la fila o con doble clic. Anular, sólo
     * con VENTA_ANULAR y en ventas que todavía se pueden anular.
     */
    private void configurarAcciones() {

        List<Accion<VentaDto>> acciones = new ArrayList<>();

        acciones.add(new Accion<>(FontAwesomeSolid.LIST, "Ver detalle", "btn-icon-edit",
                venta -> true, this::verDetalle));

        acciones.add(new Accion<>(FontAwesomeSolid.PRINT, "Imprimir comprobante", "btn-icon-edit",
                venta -> true, this::imprimir));

        if (sesionUsuario.tienePermiso(PermisoCodigo.VENTA_ANULAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.BAN, "Anular", "btn-icon-delete",
                    venta -> VentaFormato.anulable(venta.getEstadoVenta()), this::anular));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.FACTURACION_REINTENTAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.SYNC, "Reintentar la autorización en ARCA", "btn-icon-edit",
                    FacturaElectronicaFormato::faltaAutorizar, this::reintentarAutorizacion));
        }

        colAcciones.setCellFactory(column -> new RowActionsTableCell<>(acciones));

        tblVentas.setRowFactory(tabla -> {

            TableRow<VentaDto> fila = new TableRow<>();

            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) {
                    verDetalle(fila.getItem());
                }
            });

            return fila;
        });
    }

    private void verDetalle(VentaDto venta) {
        execute(() -> new VentaDetalleDialog(ventaService.obtener(venta.getId())).mostrar());
    }

    /** Imprime o reimprime el comprobante; si la venta está anulada sale marcado como ANULADA. */
    private void imprimir(VentaDto venta) {
        execute(() -> impresionComprobante.imprimir(venta));
    }

    private void anular(VentaDto venta) {

        new AnularVentaDialog(ventaService, venta).mostrar().ifPresent(anulada -> {

            String anulacion = "Venta " + anulada.getComprobante() + " anulada correctamente.";

            // Anular una factura autorizada emite la nota de crédito: se pide su CAE.
            if (!FacturaElectronicaFormato.faltaAutorizar(anulada.getNotaCredito())) {
                cargar();
                success(anulacion);
                return;
            }

            autorizacion.autorizar(anulada.getId(), (comprobantes, resultado) -> {
                cargar();
                AutorizacionComprobantes.mostrar(new FacturaElectronicaFormato.Resultado(resultado.tipo(),
                        resultado.titulo(), anulacion + "\n\n" + resultado.mensaje(), false));
            });
        });
    }

    private void reintentarAutorizacion(VentaDto venta) {
        autorizacion.autorizarYMostrar(venta.getId(), this::cargar);
    }

    private void limpiarFiltros() {

        actualizandoFiltros = true;
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        cmbEstado.setValue(null);
        txtCliente.clear();
        actualizandoFiltros = false;

        cargar();
    }

    private void cargar() {

        if (actualizandoFiltros) {
            return;
        }

        execute(() -> {
            consultadas = ventaService.listar(dpDesde.getValue(), dpHasta.getValue(), cmbEstado.getValue());
            filtrarPorCliente();
        });

        actualizarPendientesCae();
    }

    /** "Pendientes de CAE (n)", sólo con acceso a esa pantalla y si hay alguno (de cualquier fecha). */
    private void actualizarPendientesCae() {

        int pendientes = 0;

        if (navigator.puedeAcceder(AppView.FACTURACION_PENDIENTES)) {
            try {
                pendientes = facturacion.listarSinAutorizar().size();
            } catch (RuntimeException e) {
                pendientes = 0;
            }
        }

        btnPendientesCae.setText("Pendientes de CAE (" + pendientes + ")");
        btnPendientesCae.setVisible(pendientes > 0);
        btnPendientesCae.setManaged(pendientes > 0);
    }

    private void filtrarPorCliente() {

        String cliente = txtCliente.getText() == null ? "" : txtCliente.getText().trim();

        List<VentaDto> ventas = cliente.isEmpty()
                ? consultadas
                : consultadas.stream()
                .filter(v -> ComboBoxBusqueda.coincide(v.getClienteNombre(), cliente))
                .toList();

        tblVentas.getItems().setAll(ventas);

        boolean filtrado = dpDesde.getValue() != null || dpHasta.getValue() != null
                || cmbEstado.getValue() != null || !cliente.isEmpty();
        lblSinResultados.setText(filtrado ? "No hay ventas que coincidan con los filtros." : "No hay ventas.");

        lblCantidad.setText("Total: " + ventas.size() + (ventas.size() == 1 ? " venta" : " ventas"));

        // Las ventas anuladas o canceladas no suman al importe vendido.
        BigDecimal importe = ventas.stream()
                .filter(v -> VentaFormato.sumaAlImporte(v.getEstadoVenta()))
                .map(VentaDto::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        lblImporte.setText(ventas.isEmpty() ? "" : "Importe vendido: " + VentaFormato.importe(importe));
    }
}
