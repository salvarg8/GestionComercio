package org.gestionComercio.controller.reporte;

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
import javafx.scene.control.Tooltip;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.reporte.ColumnaReporte;
import org.gestionComercio.dto.reporte.Reporte;
import org.gestionComercio.enums.TipoReporte;
import org.gestionComercio.service.ReporteService;
import org.gestionComercio.view.component.SelectorFecha;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reportes de gestión. El combo ofrece sólo los reportes que permite el rol
 * del usuario; el período se usa en los reportes que lo admiten (en los de
 * inventario se muestra la situación actual). Abre con el mes en curso.
 *
 * <p>El reporte se genera al cambiar el tipo o las fechas, y con
 * "Actualizar". Lo que se exporta a PDF o Excel es exactamente el reporte
 * que está en pantalla ({@link ExportacionReporte}).
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class ReportesController extends AbstractController {

    @FXML
    private Button btnExportarPdf;

    @FXML
    private Button btnExportarExcel;

    @FXML
    private ComboBox<TipoReporte> cmbReporte;

    @FXML
    private Label lblDesde;

    @FXML
    private DatePicker dpDesde;

    @FXML
    private Label lblHasta;

    @FXML
    private DatePicker dpHasta;

    @FXML
    private Button btnActualizar;

    @FXML
    private Label lblDetalle;

    @FXML
    private TableView<List<Object>> tblReporte;

    @FXML
    private Label lblSinDatos;

    @FXML
    private Label lblResumen;

    /** Ancho máximo de una columna; un texto más largo se corta y se ve completo en el tooltip. */
    private static final double ANCHO_MAXIMO = 380;

    /** Relleno de la celda más un margen, sumado al ancho del texto. */
    private static final double RELLENO = 26;

    /** Lugar que se reserva para la barra de desplazamiento vertical. */
    private static final double BARRA_VERTICAL = 18;

    private final ReporteService reporteService;
    private final ExportacionReporte exportacionReporte;

    /** Reporte que está en pantalla; null si todavía no se generó o falló. */
    private Reporte reporte;

    /** Evita generar varias veces al cambiar varios filtros juntos. */
    private boolean actualizandoFiltros;

    @Override
    protected void initializeComponents() {

        SelectorFecha.configurar(dpDesde);
        SelectorFecha.configurar(dpHasta);

        tblReporte.setRowFactory(tabla -> new FilaReporte());

        actualizandoFiltros = true;

        LocalDate hoy = LocalDate.now();
        dpDesde.setValue(hoy.withDayOfMonth(1));
        dpHasta.setValue(hoy);

        List<TipoReporte> disponibles = reporteService.disponibles();
        cmbReporte.getItems().setAll(disponibles);

        if (!disponibles.isEmpty()) {
            cmbReporte.setValue(disponibles.get(0));
        }

        actualizandoFiltros = false;

        mostrarReporte(null);
        generar();
    }

    @Override
    protected void initializeEvents() {

        cmbReporte.valueProperty().addListener((obs, o, n) -> generar());
        dpDesde.valueProperty().addListener((obs, o, n) -> generar());
        dpHasta.valueProperty().addListener((obs, o, n) -> generar());
        btnActualizar.setOnAction(e -> generar());
        tblReporte.widthProperty().addListener((obs, o, n) -> repartirSobrante());
        btnExportarPdf.setOnAction(e -> exportarPdf());
        btnExportarExcel.setOnAction(e -> exportarExcel());
    }

    private void generar() {

        if (actualizandoFiltros) {
            return;
        }

        TipoReporte tipo = cmbReporte.getValue();

        configurarFiltros(tipo);

        if (tipo == null) {
            mostrarReporte(null);
            return;
        }

        // Si falla (permiso, período inválido), no queda en pantalla un reporte de otros filtros.
        mostrarReporte(null);

        execute(() -> mostrarReporte(reporteService.generar(tipo, dpDesde.getValue(), dpHasta.getValue())));
    }

    private void configurarFiltros(TipoReporte tipo) {

        boolean usaFechas = tipo != null && tipo.isUsaFechas();

        lblDesde.setDisable(!usaFechas);
        dpDesde.setDisable(!usaFechas);
        lblHasta.setDisable(!usaFechas);
        dpHasta.setDisable(!usaFechas);

        lblDetalle.setText(tipo == null ? "" : tipo.getDetalle());
    }

    private void mostrarReporte(Reporte nuevo) {

        reporte = nuevo;

        tblReporte.getColumns().clear();
        tblReporte.getItems().clear();

        btnExportarPdf.setDisable(nuevo == null);
        btnExportarExcel.setDisable(nuevo == null);

        if (nuevo == null) {
            lblSinDatos.setText(cmbReporte.getItems().isEmpty()
                    ? "No tiene permiso para ver ningún reporte."
                    : "Seleccione un reporte.");
            lblResumen.setText("");
            return;
        }

        List<ColumnaReporte> columnas = nuevo.columnas();

        for (int i = 0; i < columnas.size(); i++) {
            tblReporte.getColumns().add(columna(columnas.get(i), i, anchoNecesario(nuevo, i)));
        }

        repartirSobrante();

        List<List<Object>> filas = new ArrayList<>(nuevo.filas());

        // Sin datos, la tabla muestra el mensaje en lugar de una fila de totales en cero.
        if (nuevo.tieneTotales() && !nuevo.filas().isEmpty()) {
            filas.add(nuevo.totales());
        }

        tblReporte.getItems().setAll(filas);

        lblSinDatos.setText(nuevo.tipo().isUsaFechas()
                ? "No hay datos para el período seleccionado."
                : "No hay productos para mostrar.");

        int cantidad = nuevo.filas().size();
        lblResumen.setText(nuevo.filtros() + " · " + cantidad + (cantidad == 1 ? " fila" : " filas"));
    }

    /**
     * Ancho que necesita la columna para mostrar completo su texto más largo
     * (el título va en negrita, igual que la fila de totales), hasta el máximo.
     */
    private static double anchoNecesario(Reporte reporte, int indice) {

        Font normal = Font.getDefault();
        Font negrita = Font.font(normal.getFamily(), FontWeight.BOLD, normal.getSize());

        double ancho = Math.max(
                anchoTexto(reporte.columnas().get(indice).titulo(), negrita),
                anchoTexto(reporte.textoMasLargo(indice), negrita));

        return Math.min(Math.ceil(ancho) + RELLENO, ANCHO_MAXIMO);
    }

    private static double anchoTexto(String texto, Font fuente) {

        Text medida = new Text(texto);
        medida.setFont(fuente);
        return medida.getLayoutBounds().getWidth();
    }

    /**
     * Si las columnas no ocupan todo el ancho de la tabla, el sobrante se
     * reparte entre las de texto (en proporción a su ancho) para que no quede
     * un hueco a la derecha. Si no entran, aparece la barra horizontal.
     */
    private void repartirSobrante() {

        if (reporte == null || tblReporte.getColumns().isEmpty()) {
            return;
        }

        List<TableColumn<List<Object>, ?>> columnas = tblReporte.getColumns();

        double necesario = 0;
        double ajustables = 0;

        for (int i = 0; i < columnas.size(); i++) {

            double ancho = (Double) columnas.get(i).getUserData();
            necesario += ancho;

            if (reporte.columnas().get(i).esAjustable()) {
                ajustables += ancho;
            }
        }

        double sobrante = Math.max(tblReporte.getWidth() - BARRA_VERTICAL - necesario, 0);

        for (int i = 0; i < columnas.size(); i++) {

            double ancho = (Double) columnas.get(i).getUserData();
            double extra;

            if (ajustables == 0) {
                extra = sobrante * ancho / necesario;
            } else {
                extra = reporte.columnas().get(i).esAjustable() ? sobrante * ancho / ajustables : 0;
            }

            columnas.get(i).setPrefWidth(Math.floor(ancho + extra));
        }
    }

    private TableColumn<List<Object>, String> columna(ColumnaReporte definicion, int indice, double ancho) {

        TableColumn<List<Object>, String> columna = new TableColumn<>(definicion.titulo());

        columna.setSortable(false);
        columna.setReorderable(false);
        columna.setUserData(ancho);
        columna.setPrefWidth(ancho);

        columna.setCellValueFactory(c -> new SimpleStringProperty(
                definicion.formato().formatear(c.getValue().get(indice))));

        boolean numerica = definicion.formato().esNumerico();
        boolean limitada = ancho >= ANCHO_MAXIMO;

        columna.setCellFactory(c -> new TableCell<>() {

            @Override
            protected void updateItem(String texto, boolean empty) {

                super.updateItem(texto, empty);
                setText(empty ? null : texto);

                // En una columna que llegó al ancho máximo, el texto largo se ve completo al pasar el mouse.
                setTooltip(limitada && !empty && texto != null && !texto.isEmpty() ? new Tooltip(texto) : null);

                getStyleClass().remove("celda-numerica");

                if (numerica) {
                    getStyleClass().add("celda-numerica");
                }
            }
        });

        return columna;
    }

    private void exportarPdf() {

        if (reporte != null) {
            execute(() -> exportacionReporte.exportarPdf(reporte, tblReporte.getScene().getWindow()));
        }
    }

    private void exportarExcel() {

        if (reporte != null) {
            execute(() -> exportacionReporte.exportarExcel(reporte, tblReporte.getScene().getWindow()));
        }
    }

    /** Marca en negrita la fila de totales del reporte en pantalla. */
    private final class FilaReporte extends TableRow<List<Object>> {

        @Override
        protected void updateItem(List<Object> fila, boolean empty) {

            super.updateItem(fila, empty);

            getStyleClass().remove("fila-total");

            if (!empty && reporte != null && fila == reporte.totales()) {
                getStyleClass().add("fila-total");
            }
        }
    }
}
