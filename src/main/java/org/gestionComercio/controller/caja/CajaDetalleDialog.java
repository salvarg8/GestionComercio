package org.gestionComercio.controller.caja;

import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.dto.caja.MovimientoCajaDto;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.util.dialog.Dialogs;

import java.util.List;
import java.util.Objects;

/**
 * Detalle de una caja cerrada: el arqueo y todos sus movimientos. Sólo lectura.
 */
class CajaDetalleDialog {

    private final CajaDto caja;
    private final List<MovimientoCajaDto> movimientos;

    CajaDetalleDialog(CajaDto caja, List<MovimientoCajaDto> movimientos) {
        this.caja = caja;
        this.movimientos = movimientos;
    }

    void mostrar() {

        VBox cuerpo = new VBox(12, resumen(), tabla());

        Dialogs.crear(Dialogs.Tipo.INFORMACION, "Caja #" + caja.getId(), cuerpo, Dialogs.ACEPTAR).showAndWait();
    }

    private GridPane resumen() {

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);

        ColumnConstraints etiquetas = new ColumnConstraints();
        etiquetas.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().setAll(etiquetas, new ColumnConstraints(), etiquetas, new ColumnConstraints());

        Label diferencia = new Label(CajaFormato.textoDiferencia(caja.getDiferencia()));
        diferencia.getStyleClass().setAll("label", "estado-badge", CajaFormato.estiloDiferencia(caja.getDiferencia()));

        grid.addRow(0, new Label("Responsable:"),
                new Label(CajaFormato.usuario(caja.getUsuarioNombreCompleto(), caja.getUsuarioNombre())),
                new Label("Saldo esperado:"), negrita(CajaFormato.importe(caja.getSaldoEsperado())));
        grid.addRow(1, new Label("Apertura:"), new Label(DateUtils.format(caja.getFechaApertura())),
                new Label("Saldo contado:"), negrita(CajaFormato.importe(caja.getSaldoFinal())));
        grid.addRow(2, new Label("Cierre:"), new Label(DateUtils.format(caja.getFechaCierre())),
                new Label("Diferencia:"), diferencia);

        grid.addRow(3, new Label("Abrió:"),
                new Label(CajaFormato.usuario(caja.getUsuarioAperturaNombreCompleto(), caja.getUsuarioAperturaNombre())),
                new Label("Cerró:"),
                new Label(CajaFormato.usuario(caja.getUsuarioCierreNombreCompleto(), caja.getUsuarioCierreNombre())));

        if (caja.getObservacionCierre() != null) {
            Label observacion = new Label(caja.getObservacionCierre());
            observacion.setWrapText(true);
            grid.add(new Label("Observación:"), 0, 4);
            grid.add(observacion, 1, 4, 3, 1);
        }

        return grid;
    }

    private TableView<MovimientoCajaDto> tabla() {

        TableView<MovimientoCajaDto> tabla = new TableView<>();
        tabla.getStyleClass().add("tabla-centrada");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPrefSize(760, 320);
        tabla.setPlaceholder(new Label("La caja no tiene movimientos."));

        TableColumn<MovimientoCajaDto, String> colFecha = columna("Fecha", 120);
        TableColumn<MovimientoCajaDto, TipoMovimientoCaja> colTipo = new TableColumn<>("Tipo");
        colTipo.setPrefWidth(120);
        colTipo.setSortable(false);
        TableColumn<MovimientoCajaDto, String> colDescripcion = columna("Descripción", 220);
        TableColumn<MovimientoCajaDto, String> colImporte = columna("Importe", 120);
        TableColumn<MovimientoCajaDto, String> colUsuario = columna("Usuario", 160);

        CajaFormato.configurarColumnasMovimientos(colFecha, colTipo, colDescripcion, colImporte, colUsuario);

        tabla.getColumns().setAll(List.of(colFecha, colTipo, colDescripcion, colImporte, colUsuario));
        tabla.getItems().setAll(Objects.requireNonNullElse(movimientos, List.of()));

        return tabla;
    }

    private static TableColumn<MovimientoCajaDto, String> columna(String titulo, double ancho) {
        TableColumn<MovimientoCajaDto, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna.setSortable(false);
        return columna;
    }

    private static Label negrita(String texto) {
        Label label = new Label(texto);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }
}
