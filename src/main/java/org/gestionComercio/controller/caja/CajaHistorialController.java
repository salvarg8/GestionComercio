package org.gestionComercio.controller.caja;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.caja.CajaDto;
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
 * Historial de cajas cerradas de todos los usuarios, filtrable por fecha de
 * cierre. Cada caja muestra su arqueo (saldo esperado, contado y diferencia);
 * desde la fila se abre el detalle con sus movimientos ({@link CajaDetalleDialog}).
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class CajaHistorialController extends AbstractController {

    @FXML
    private Button btnVolver;

    @FXML
    private DatePicker dpDesde;

    @FXML
    private DatePicker dpHasta;

    @FXML
    private Button btnLimpiar;

    @FXML
    private TableView<CajaDto> tblCajas;

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

        dpDesde.valueProperty().addListener((obs, o, n) -> cargar());
        dpHasta.valueProperty().addListener((obs, o, n) -> cargar());
        btnLimpiar.setOnAction(e -> limpiarFiltros());
        btnVolver.setOnAction(e -> navigator.navigate(AppView.CAJA));
    }

    private void configurarColumnas() {

        colApertura.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.format(c.getValue().getFechaApertura())));
        colCierre.setCellValueFactory(c -> new SimpleStringProperty(DateUtils.format(c.getValue().getFechaCierre())));
        colUsuario.setCellValueFactory(c -> new SimpleStringProperty(
                CajaFormato.usuario(c.getValue().getUsuarioNombreCompleto(), c.getValue().getUsuarioNombre())));

        colSaldoInicial.setCellValueFactory(c -> importe(c.getValue(), CajaDto::getSaldoInicial));
        colIngresos.setCellValueFactory(c -> importe(c.getValue(), CajaDto::getTotalIngresos));
        colEgresos.setCellValueFactory(c -> importe(c.getValue(), CajaDto::getTotalEgresos));
        colSaldoEsperado.setCellValueFactory(c -> importe(c.getValue(), CajaDto::getSaldoEsperado));
        colSaldoContado.setCellValueFactory(c -> importe(c.getValue(), CajaDto::getSaldoFinal));

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

            List<CajaDto> cajas = cajaService.listarCajasCerradas(dpDesde.getValue(), dpHasta.getValue());

            tblCajas.getItems().setAll(cajas);

            boolean filtrado = dpDesde.getValue() != null || dpHasta.getValue() != null;
            lblSinResultados.setText(filtrado
                    ? "No hay cajas cerradas en el período indicado."
                    : "No hay cajas cerradas.");

            lblTotal.setText("Total: " + cajas.size() + (cajas.size() == 1 ? " caja" : " cajas"));

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
