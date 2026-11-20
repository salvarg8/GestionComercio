package org.gestionComercio.controller.compra;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.dto.compra.DetalleCompraDto;
import org.gestionComercio.enums.EstadoCompra;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.util.dialog.Dialogs;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Detalle de una compra: datos generales (y de la anulación, si está
 * anulada), renglones y total. Sólo lectura.
 */
class CompraDetalleDialog {

    private final CompraDto compra;

    CompraDetalleDialog(CompraDto compra) {
        this.compra = compra;
    }

    void mostrar() {

        VBox cuerpo = new VBox(12, datos(), tabla(), total());

        Dialogs.crear(Dialogs.Tipo.INFORMACION, CompraFormato.titulo(compra),
                cuerpo, Dialogs.ACEPTAR).showAndWait();
    }

    private GridPane datos() {

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);

        ColumnConstraints etiquetas = new ColumnConstraints();
        etiquetas.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().setAll(etiquetas, new ColumnConstraints(), etiquetas, new ColumnConstraints());

        grid.addRow(0, new Label("Proveedor:"), new Label(Objects.toString(compra.getProveedorNombre(), "")),
                new Label("Factura:"), new Label(CompraFormato.factura(compra.getNumeroFactura())));
        grid.addRow(1, new Label("Fecha:"), new Label(DateUtils.format(compra.getFecha())),
                new Label("Forma de pago:"), new Label(Objects.toString(compra.getFormaPago(), "")));
        grid.addRow(2, new Label("Registró:"),
                new Label(CompraFormato.usuario(compra.getUsuarioNombreCompleto(), compra.getUsuarioNombre())),
                new Label("Estado:"), CompraFormato.badgeEstado(compra.getEstadoCompra()));

        if (compra.getEstadoCompra() == EstadoCompra.ANULADA) {

            grid.addRow(3, new Label("Anulada el:"), new Label(DateUtils.format(compra.getFechaAnulacion())),
                    new Label("Anulada por:"), new Label(Objects.toString(compra.getUsuarioAnulacionNombreCompleto(), "")));

            Label motivo = new Label(Objects.toString(compra.getMotivoAnulacion(), ""));
            motivo.setWrapText(true);
            motivo.setMaxWidth(600);
            grid.add(new Label("Motivo:"), 0, 4);
            grid.add(motivo, 1, 4, 3, 1);
        }

        return grid;
    }

    private TableView<DetalleCompraDto> tabla() {

        TableView<DetalleCompraDto> tabla = new TableView<>();
        tabla.getStyleClass().add("tabla-centrada");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPrefSize(720, 280);
        tabla.setPlaceholder(new Label("La compra no tiene renglones."));

        tabla.getColumns().setAll(List.of(
                columna("Código", 110, DetalleCompraDto::getProductoCodigo),
                columna("Producto", 260, DetalleCompraDto::getProductoNombre),
                columna("Cantidad", 90, d -> CantidadUtils.formatear(d.getCantidad())),
                columna("Costo unitario", 120, d -> CompraFormato.importe(d.getPrecioCompra())),
                columna("Subtotal", 120, d -> CompraFormato.importe(d.getSubtotal()))));

        tabla.getItems().setAll(Objects.requireNonNullElse(compra.getDetalles(), List.of()));

        return tabla;
    }

    private HBox total() {

        Label total = new Label(CompraFormato.importe(compra.getTotal()));
        total.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        HBox fila = new HBox(12, new Label("Total:"), total);
        fila.setAlignment(Pos.CENTER_RIGHT);

        return fila;
    }

    private static TableColumn<DetalleCompraDto, String> columna(
            String titulo, double ancho, Function<DetalleCompraDto, String> valor) {

        TableColumn<DetalleCompraDto, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna.setSortable(false);
        columna.setCellValueFactory(c -> new SimpleStringProperty(Objects.toString(valor.apply(c.getValue()), "")));
        return columna;
    }
}
