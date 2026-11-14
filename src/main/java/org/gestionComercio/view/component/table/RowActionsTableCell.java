package org.gestionComercio.view.component.table;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Columna de acciones con botones de ícono que pueden depender de la fila
 * (por ejemplo, "Dar de baja" en las activas y "Habilitar" en las dadas de baja).
 * Para el caso simple de editar/eliminar sigue estando {@link ActionTableCell}.
 */
public class RowActionsTableCell<T> extends TableCell<T, Void> {

    /**
     * @param estilo clase CSS del ícono: btn-icon-edit, btn-icon-delete o btn-icon-enable
     * @param visible en qué filas se muestra la acción
     */
    public record Accion<T>(
            Ikon icono,
            String tooltip,
            String estilo,
            Predicate<T> visible,
            Consumer<T> handler) {
    }

    private final List<Accion<T>> acciones;
    private final HBox container = new HBox(8);

    public RowActionsTableCell(List<Accion<T>> acciones) {
        this.acciones = acciones;
        container.setAlignment(Pos.CENTER);
    }

    @Override
    protected void updateItem(Void item, boolean empty) {

        super.updateItem(item, empty);

        T fila = empty || getTableRow() == null ? null : getTableRow().getItem();

        if (fila == null) {
            setGraphic(null);
            return;
        }

        container.getChildren().clear();

        for (Accion<T> accion : acciones) {
            if (accion.visible().test(fila)) {
                container.getChildren().add(boton(accion, fila));
            }
        }

        setGraphic(container);
    }

    private Button boton(Accion<T> accion, T fila) {

        Button boton = new Button("", new FontIcon(accion.icono()));

        boton.getStyleClass().addAll("btn-icon", accion.estilo());
        boton.setTooltip(new Tooltip(accion.tooltip()));
        boton.setOnAction(e -> accion.handler().accept(fila));

        return boton;
    }
}
