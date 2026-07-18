package org.gestionComercio.controller.base;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public abstract class AbstractTableController<T>
        extends AbstractController {

    protected ObservableList<T> data =
            FXCollections.observableArrayList();

    protected abstract void loadData();

    protected abstract void search();

    protected abstract void delete();

}