package org.gestionComercio.controller.facturacion;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.enums.EstadoComprobanteArca;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.FacturacionElectronicaService;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.util.dialog.Dialogs;
import org.gestionComercio.view.component.TareaEnSegundoPlano;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Comprobantes electrónicos sin CAE (pendientes, enviados sin respuesta o
 * rechazados), con el reintento de uno o de todos los pendientes. Requiere
 * FACTURACION_REINTENTAR.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class ComprobantesPendientesController extends AbstractController {

    @FXML
    private Button btnConfiguracion;

    @FXML
    private Button btnActualizar;

    @FXML
    private Label lblAviso;

    @FXML
    private TableView<ComprobanteArcaDto> tblComprobantes;

    @FXML
    private TableColumn<ComprobanteArcaDto, String> colFecha;

    @FXML
    private TableColumn<ComprobanteArcaDto, String> colComprobante;

    @FXML
    private TableColumn<ComprobanteArcaDto, String> colCliente;

    @FXML
    private TableColumn<ComprobanteArcaDto, String> colTotal;

    @FXML
    private TableColumn<ComprobanteArcaDto, ComprobanteArcaDto> colEstado;

    @FXML
    private TableColumn<ComprobanteArcaDto, String> colIntento;

    @FXML
    private TableColumn<ComprobanteArcaDto, String> colObservaciones;

    @FXML
    private Label lblCantidad;

    @FXML
    private Button btnReintentar;

    @FXML
    private Button btnReintentarTodos;

    private final FacturacionElectronicaService facturacion;
    private final AutorizacionComprobantes autorizacion;
    private final Navigator navigator;

    @Override
    protected void initializeComponents() {

        columna(colFecha, c -> DateUtils.format(c.getVentaFecha()));
        columna(colComprobante, ComprobanteArcaDto::getComprobante);
        columna(colCliente, ComprobanteArcaDto::getClienteNombre);
        columna(colTotal, c -> c.getImporteTotal() == null ? "" : CurrencyUtils.format(c.getImporteTotal()));
        columna(colIntento, c -> DateUtils.format(c.getFechaProceso()));

        colEstado.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue()));
        colEstado.setCellFactory(columna -> new TableCell<>() {
            @Override
            protected void updateItem(ComprobanteArcaDto comprobante, boolean empty) {
                super.updateItem(comprobante, empty);
                setGraphic(empty || comprobante == null ? null : FacturaElectronicaFormato.badge(comprobante));
            }
        });

        colObservaciones.setCellValueFactory(c -> new SimpleStringProperty(
                Objects.toString(c.getValue().getObservaciones(), "")));
        colObservaciones.setCellFactory(columna -> new TableCell<>() {

            private final Label texto = new Label();

            {
                texto.setWrapText(true);
                texto.maxWidthProperty().bind(columna.widthProperty().subtract(12));
            }

            @Override
            protected void updateItem(String observaciones, boolean empty) {
                super.updateItem(observaciones, empty);
                texto.setText(observaciones);
                setGraphic(empty ? null : texto);
            }
        });

        boolean configura = navigator.puedeAcceder(AppView.FACTURACION_CONFIG);
        btnConfiguracion.setVisible(configura);
        btnConfiguracion.setManaged(configura);

        cargar();
    }

    @Override
    protected void initializeBindings() {
        btnReintentar.disableProperty().bind(tblComprobantes.getSelectionModel().selectedItemProperty().isNull());
    }

    @Override
    protected void initializeEvents() {

        btnActualizar.setOnAction(e -> cargar());
        btnConfiguracion.setOnAction(e -> navigator.navigate(AppView.FACTURACION_CONFIG));
        btnReintentar.setOnAction(e -> reintentarSeleccionado());
        btnReintentarTodos.setOnAction(e -> reintentarTodos());
    }

    private void cargar() {

        execute(() -> {

            boolean habilitada = facturacion.estaHabilitada();

            lblAviso.setText(habilitada ? "" : "La facturación electrónica no está habilitada: no se pueden pedir CAE "
                    + "hasta habilitarla en la configuración de ARCA.");
            lblAviso.setVisible(!habilitada);
            lblAviso.setManaged(!habilitada);

            List<ComprobanteArcaDto> comprobantes = facturacion.listarSinAutorizar();
            tblComprobantes.getItems().setAll(comprobantes);

            long pendientes = comprobantes.stream().filter(c -> c.getEstado() != EstadoComprobanteArca.RECHAZADO).count();

            lblCantidad.setText(comprobantes.size() + (comprobantes.size() == 1 ? " comprobante" : " comprobantes")
                    + " sin CAE (" + pendientes + " para reintentar, "
                    + (comprobantes.size() - pendientes) + " rechazados)");

            btnReintentarTodos.setDisable(!habilitada || pendientes == 0);
        });
    }

    private void reintentarSeleccionado() {

        ComprobanteArcaDto seleccionado = tblComprobantes.getSelectionModel().getSelectedItem();

        if (seleccionado != null) {
            autorizacion.autorizarYMostrar(seleccionado.getVentaId(), this::cargar);
        }
    }

    /** Los pendientes y enviados sin respuesta; los rechazados se reintentan de a uno, después de corregirlos. */
    private void reintentarTodos() {

        TareaEnSegundoPlano.ejecutar("Pidiendo el CAE de los comprobantes pendientes…",
                facturacion::autorizarPendientes,
                procesados -> {
                    cargar();
                    mostrarResumen(procesados);
                },
                error -> {
                    cargar();
                    TareaEnSegundoPlano.mostrarError(error);
                });
    }

    private static void mostrarResumen(List<ComprobanteArcaDto> procesados) {

        if (procesados.isEmpty()) {
            Dialogs.informacion("Sin comprobantes pendientes", "No había comprobantes pendientes para reintentar.");
            return;
        }

        Map<EstadoComprobanteArca, Long> porEstado = procesados.stream()
                .collect(Collectors.groupingBy(ComprobanteArcaDto::getEstado, Collectors.counting()));

        long autorizados = porEstado.getOrDefault(EstadoComprobanteArca.AUTORIZADO, 0L);
        long rechazados = porEstado.getOrDefault(EstadoComprobanteArca.RECHAZADO, 0L);
        long sinCae = procesados.size() - autorizados - rechazados;

        String mensaje = "Autorizados: " + autorizados + "\nRechazados: " + rechazados
                + "\nSiguen pendientes: " + sinCae
                + (sinCae > 0 ? "\n\nLos que siguen pendientes muestran el motivo en la lista (por ejemplo, sin "
                + "conexión con ARCA). Se pueden reintentar más tarde." : "")
                + (rechazados > 0 ? "\n\nRevise el motivo de los rechazados, corríjalo y reintente cada uno." : "");

        if (autorizados == procesados.size()) {
            Dialogs.exito("Comprobantes autorizados", mensaje);
        } else {
            Dialogs.advertencia("Algunos comprobantes no se autorizaron", mensaje);
        }
    }

    private static void columna(TableColumn<ComprobanteArcaDto, String> columna,
                                Function<ComprobanteArcaDto, String> valor) {
        columna.setCellValueFactory(c -> new SimpleStringProperty(Objects.toString(valor.apply(c.getValue()), "")));
    }

}
