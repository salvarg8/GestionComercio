package org.gestionComercio.view.component.table;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;


import java.util.function.Consumer;

public class ActionTableCell<T> extends TableCell<T, Void> {

    Button btnEditar = new Button("", new FontIcon(FontAwesomeSolid.EDIT));
    Button btnEliminar = new Button("", new FontIcon(FontAwesomeSolid.TRASH));
    private final HBox container = new HBox(8);

    public ActionTableCell(
            Consumer<T> onEditar,
            Consumer<T> onEliminar) {

        container.setAlignment(Pos.CENTER);
        container.getChildren().addAll(btnEditar, btnEliminar);

        btnEditar.setOnAction(e ->
                onEditar.accept(getTableRow().getItem()));

        btnEliminar.setOnAction(e ->
                onEliminar.accept(getTableRow().getItem()));
    }

    @Override
    protected void updateItem(Void item, boolean empty) {

        super.updateItem(item, empty);

        if (empty || getTableRow().getItem() == null) {
            setGraphic(null);
        } else {
            setGraphic(container);
        }
    }
}