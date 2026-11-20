package org.gestionComercio.view.component;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.dialog.Dialogs;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Elige un producto entre varios que coinciden con la búsqueda. Se maneja con
 * teclado: flechas para moverse, Enter para elegir y Escape para cancelar.
 *
 * <p>La columna de precio la elige cada pantalla: el punto de venta muestra
 * el precio de venta y las compras, el precio de compra.
 */
public class SeleccionProductoDialog {

    private final List<ProductoDto> productos;
    private final String busqueda;
    private final String tituloPrecio;
    private final Function<ProductoDto, BigDecimal> precio;

    public SeleccionProductoDialog(List<ProductoDto> productos, String busqueda,
                                   String tituloPrecio, Function<ProductoDto, BigDecimal> precio) {
        this.productos = productos;
        this.busqueda = busqueda;
        this.tituloPrecio = tituloPrecio;
        this.precio = precio;
    }

    public Optional<ProductoDto> mostrar() {

        TableView<ProductoDto> tabla = tabla();

        ButtonType elegir = new ButtonType("Agregar", ButtonData.OK_DONE);

        Label intro = new Label(productos.size() + " productos coinciden con \"" + busqueda
                + "\". Elija uno con las flechas y Enter.");
        intro.setWrapText(true);

        Alert alert = Dialogs.crear(Dialogs.Tipo.CONFIRMACION, "Elegir producto",
                new VBox(10, intro, tabla), Dialogs.CANCELAR, elegir);

        Button botonElegir = (Button) alert.getDialogPane().lookupButton(elegir);
        botonElegir.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());

        // Enter o doble clic en la tabla eligen el producto marcado.
        tabla.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && tabla.getSelectionModel().getSelectedItem() != null) {
                e.consume();
                botonElegir.fire();
            }
        });

        tabla.setRowFactory(t -> {
            TableRow<ProductoDto> fila = new TableRow<>();
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) {
                    botonElegir.fire();
                }
            });
            return fila;
        });

        tabla.getSelectionModel().selectFirst();
        Platform.runLater(tabla::requestFocus);

        Optional<ButtonType> respuesta = alert.showAndWait();

        return respuesta.isPresent() && respuesta.get() == elegir
                ? Optional.ofNullable(tabla.getSelectionModel().getSelectedItem())
                : Optional.empty();
    }

    private TableView<ProductoDto> tabla() {

        TableView<ProductoDto> tabla = new TableView<>();
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPrefSize(720, 340);

        tabla.getColumns().setAll(List.of(
                columna("Código", 110, ProductoDto::getCodigo),
                columna("Producto", 280, ProductoDto::getNombre),
                columna(tituloPrecio, 110, p -> CurrencyUtils.format(precio.apply(p))),
                columna("Disponible", 110, SeleccionProductoDialog::disponible)));

        tabla.getItems().setAll(productos);

        return tabla;
    }

    /** Stock disponible con su unidad, o "Sin control" si el producto no maneja stock. */
    public static String disponible(ProductoDto producto) {

        if (!Boolean.TRUE.equals(producto.getManejaStock())) {
            return "Sin control";
        }

        String unidad = producto.getUnidadMedida() == null
                ? "" : " (" + producto.getUnidadMedida().getDescripcion().toLowerCase() + ")";

        return CantidadUtils.formatear(producto.getStock()) + unidad;
    }

    private static TableColumn<ProductoDto, String> columna(
            String titulo, double ancho, Function<ProductoDto, String> valor) {

        TableColumn<ProductoDto, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna.setSortable(false);
        columna.setCellValueFactory(c -> new SimpleStringProperty(Objects.toString(valor.apply(c.getValue()), "")));
        return columna;
    }
}
