package org.gestionComercio.controller.venta;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.util.dialog.Dialogs;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Detalle de una venta: datos generales (y de la anulación, si está
 * anulada), renglones y totales. Sólo lectura.
 */
class VentaDetalleDialog {

    private final VentaDto venta;

    VentaDetalleDialog(VentaDto venta) {
        this.venta = venta;
    }

    void mostrar() {

        VBox cuerpo = new VBox(12, datos(), tabla(), totales());

        Dialogs.crear(Dialogs.Tipo.INFORMACION, "Venta " + VentaFormato.numero(venta.getId()),
                cuerpo, Dialogs.ACEPTAR).showAndWait();
    }

    private GridPane datos() {

        GridPane grid = grilla();

        grid.addRow(0, new Label("Fecha:"), new Label(DateUtils.format(venta.getFecha())),
                new Label("Forma de pago:"), new Label(Objects.toString(venta.getFormaPago(), "")));
        grid.addRow(1, new Label("Cliente:"), new Label(Objects.toString(venta.getClienteNombre(), "")),
                new Label("Estado:"), VentaFormato.badgeEstado(venta.getEstadoVenta()));
        grid.addRow(2, new Label("Vendedor:"),
                new Label(VentaFormato.usuario(venta.getUsuarioNombreCompleto(), venta.getUsuarioNombre())));

        if (venta.getEstadoVenta() == EstadoVenta.ANULADA) {

            grid.addRow(3, new Label("Anulada el:"), new Label(DateUtils.format(venta.getFechaAnulacion())),
                    new Label("Anulada por:"), new Label(Objects.toString(venta.getUsuarioAnulacionNombreCompleto(), "")));

            Label motivo = new Label(Objects.toString(venta.getMotivoAnulacion(), ""));
            motivo.setWrapText(true);
            motivo.setMaxWidth(600);
            grid.add(new Label("Motivo:"), 0, 4);
            grid.add(motivo, 1, 4, 3, 1);
        }

        return grid;
    }

    private TableView<DetalleVentaDto> tabla() {

        TableView<DetalleVentaDto> tabla = new TableView<>();
        tabla.getStyleClass().add("tabla-centrada");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPrefSize(720, 280);
        tabla.setPlaceholder(new Label("La venta no tiene renglones."));

        tabla.getColumns().setAll(List.of(
                columna("Código", 110, DetalleVentaDto::getProductoCodigo),
                columna("Producto", 260, DetalleVentaDto::getProductoNombre),
                columna("Cantidad", 90, d -> CantidadUtils.formatear(d.getCantidad())),
                columna("Precio unitario", 120, d -> VentaFormato.importe(d.getPrecioUnitario())),
                columna("Subtotal", 120, d -> VentaFormato.importe(d.getSubtotal()))));

        tabla.getItems().setAll(Objects.requireNonNullElse(venta.getDetalles(), List.of()));

        return tabla;
    }

    private GridPane totales() {

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        grid.setAlignment(Pos.CENTER_RIGHT);

        ColumnConstraints etiquetas = new ColumnConstraints();
        etiquetas.setHalignment(HPos.RIGHT);
        ColumnConstraints valores = new ColumnConstraints();
        valores.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().setAll(etiquetas, valores);

        Label total = new Label(VentaFormato.importe(venta.getTotal()));
        total.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");

        grid.addRow(0, new Label("Subtotal:"), new Label(VentaFormato.importe(venta.getSubtotal())));
        grid.addRow(1, new Label("Descuento:"), new Label("− " + VentaFormato.importe(venta.getDescuento())));
        grid.addRow(2, new Label("Total:"), total);

        return grid;
    }

    private static GridPane grilla() {

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);

        ColumnConstraints etiquetas = new ColumnConstraints();
        etiquetas.setHalignment(HPos.RIGHT);
        grid.getColumnConstraints().setAll(etiquetas, new ColumnConstraints(), etiquetas, new ColumnConstraints());

        return grid;
    }

    private static TableColumn<DetalleVentaDto, String> columna(
            String titulo, double ancho, Function<DetalleVentaDto, String> valor) {

        TableColumn<DetalleVentaDto, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna.setSortable(false);
        columna.setCellValueFactory(c -> new SimpleStringProperty(Objects.toString(valor.apply(c.getValue()), "")));
        return columna;
    }
}
