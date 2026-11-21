package org.gestionComercio.controller.caja;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.FiltroCaja;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.CajaService;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.view.component.table.RowActionsTableCell;
import org.gestionComercio.view.component.table.RowActionsTableCell.Accion;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Historial de cajas abiertas y cerradas, filtrable por estado y fechas
 * (ver {@link CajaService#listarHistorial}). Se ven las cajas propias y, con
 * CAJA_VER_ABIERTAS_TODAS / CAJA_VER_CERRADAS_TODAS, las abiertas / cerradas
 * de los demás usuarios. Cada caja cerrada muestra su arqueo (saldo esperado,
 * contado y diferencia); desde la fila se abre el detalle con sus
 * movimientos ({@link CajaDetalleDialog}).
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class CajaHistorialController extends AbstractController {

    @FXML
    private Button btnVolver;

    @FXML
    private ComboBox<FiltroCaja> cmbEstado;

    @FXML
    private DatePicker dpDesde;

    @FXML
    private DatePicker dpHasta;

    @FXML
    private Button btnLimpiar;

    @FXML
    private TableView<CajaDto> tblCajas;

    @FXML
    private TableColumn<CajaDto, EstadoCaja> colEstado;

    @FXML
    private TableColumn<CajaDto, String> colApertura;

    @FXML
    private TableColumn<CajaDto, String> colCierre;

    @FXML
    private TableColumn<CajaDto, String> colUsuario;

    @FXML
    private TableColumn<CajaDto, String> colSaldoInicial;

    @FXML
    private TableColumn<CajaDto, String> colIngresos;

    @FXML
    private TableColumn<CajaDto, String> colEgresos;

    @FXML
    private TableColumn<CajaDto, String> colSaldoEsperado;

    @FXML
    private TableColumn<CajaDto, String> colSaldoContado;

    @FXML
    private TableColumn<CajaDto, BigDecimal> colDiferencia;

    @FXML
    private TableColumn<CajaDto, Void> colAcciones;

    @FXML
    private Label lblSinResultados;

    @FXML
    private Label lblTotal;

    @FXML
    private Label lblDiferenciaAcumulada;

    private final CajaService cajaService;
    private final Navigator navigator;

    /** Evita consultar dos veces al limpiar ambos filtros juntos. */
    private boolean actualizandoFiltros;

    @Override
    protected void initializeComponents() {

        cmbEstado.getItems().setAll(FiltroCaja.values());
        cmbEstado.setValue(FiltroCaja.TODAS);
        CajaFormato.configurarFecha(dpDesde);
        CajaFormato.configurarFecha(dpHasta);
        configurarColumnas();
        configurarAcciones();

        boolean puedeVolver = navigator.puedeAcceder(AppView.CAJA);
        btnVolver.setVisible(puedeVolver);
        btnVolver.setManaged(puedeVolver);

        cargar();
    }

    @Override
    protected void initializeEvents() {

        cmbEstado.valueProperty().addListener((obs, o, n) -> cargar());
        dpDesde.valueProperty().addListener((obs, o, n) -> cargar());
        dpHasta.valueProperty().addListener((obs, o, n) -> cargar());
        btnLimpiar.setOnAction(e -> limpiarFiltros());
        btnVolver.setOnAction(e -> navigator.navigate(AppView.CAJA));
    }

    private void configurarColumnas() {

        colEstado.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getEstadoCaja()));
        colEstado.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(EstadoCaja estado, boolean empty) {

                super.updateItem(estado, empty);

                if (empty || estado == null) {
                    setGraphic(null);
                    return;
                }

                boolean abierta = estado == EstadoCaja.ABIERTA;
                badge.setText(abierta ? "Abierta" : "Cerrada");
                badge.getStyleClass().setAll("label", "estado-badge", abierta ? "estado-activo" : "estado-inactivo");

                setGraphic(badge);
            }
        });

        colApertura.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.format(c.getValue().getFechaApertura())));
        colCierre.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.format(c.getValue().getFechaCierre())));
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(
                CajaFormato.usuario(c.getValue().getUsuarioNombreCompleto(), c.getValue().getUsuarioNombre())));

        colSaldoInicial.setCellValueFactory(c -> importe(c.getValue(), CajaDto::getSaldoInicial));
        colIngresos.setCellValueFactory(c -> importe(c.getValue(), CajaDto::getTotalIngresos));
        colEgresos.setCellValueFactory(c -> importe(c.getValue(), CajaDto::getTotalEgresos));
        colSaldoEsperado.setCellValueFactory(c -> importe(c.getValue(), CajaDto::getSaldoEsperado));
        // Una caja abierta todavía no tiene arqueo.
        colSaldoContado.setCellValueFactory(c -> c.getValue().getEstadoCaja() == EstadoCaja.ABIERTA
                ? new SimpleStringProperty("")
                : importe(c.getValue(), CajaDto::getSaldoFinal));

        colDiferencia.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getDiferencia()));
        colDiferencia.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(BigDecimal diferencia, boolean empty) {

                super.updateItem(diferencia, empty);

                if (empty || diferencia == null) {
                    setGraphic(null);
                    return;
                }

                badge.setText(CajaFormato.textoDiferencia(diferencia));
                badge.getStyleClass().setAll("label", "estado-badge", CajaFormato.estiloDiferencia(diferencia));

                setGraphic(badge);
            }
        });
    }

    private static SimpleStringProperty importe(CajaDto caja, Function<CajaDto, BigDecimal> getter) {
        return new SimpleStringProperty(CajaFormato.importe(getter.apply(caja)));
    }

    /** Ver detalle desde el ícono de la fila o con doble clic. */
    private void configurarAcciones() {

        colAcciones.setCellFactory(column -> new RowActionsTableCell<>(List.of(
                new Accion<CajaDto>(FontAwesomeSolid.LIST, "Ver movimientos", "btn-icon-edit",
                        caja -> true, this::verDetalle))));

        tblCajas.setRowFactory(tabla -> {

            TableRow<CajaDto> fila = new TableRow<>();

            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) {
                    verDetalle(fila.getItem());
                }
            });

            return fila;
        });
    }

    private void verDetalle(CajaDto caja) {
        execute(() -> new CajaDetalleDialog(caja, cajaService.listarMovimientos(caja.getId())).mostrar());
    }

    private void limpiarFiltros() {

        actualizandoFiltros = true;
        cmbEstado.setValue(FiltroCaja.TODAS);
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        actualizandoFiltros = false;

        cargar();
    }

    private void cargar() {

        if (actualizandoFiltros) {
            return;
        }

        execute(() -> {

            FiltroCaja filtro = cmbEstado.getValue() == null ? FiltroCaja.TODAS : cmbEstado.getValue();

            List<CajaDto> cajas = cajaService.listarHistorial(dpDesde.getValue(), dpHasta.getValue(), filtro);

            tblCajas.getItems().setAll(cajas);

            String cuales = switch (filtro) {
                case ABIERTAS -> "cajas abiertas";
                case CERRADAS -> "cajas cerradas";
                case TODAS -> "cajas";
            };
            boolean filtrado = dpDesde.getValue() != null || dpHasta.getValue() != null;
            lblSinResultados.setText("No hay " + cuales + (filtrado ? " en el período indicado." : "."));

            long abiertas = cajas.stream().filter(c -> c.getEstadoCaja() == EstadoCaja.ABIERTA).count();
            lblTotal.setText("Total: " + cajas.size() + (cajas.size() == 1 ? " caja" : " cajas")
                    + (abiertas > 0 ? " (" + abiertas + (abiertas == 1 ? " abierta)" : " abiertas)") : ""));

            BigDecimal acumulada = cajas.stream()
                    .map(CajaDto::getDiferencia)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            lblDiferenciaAcumulada.setText(cajas.isEmpty()
                    ? ""
                    : "Diferencia acumulada: " + CajaFormato.textoDiferencia(acumulada));
        });
    }
}
