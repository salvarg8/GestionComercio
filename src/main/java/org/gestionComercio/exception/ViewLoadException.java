package org.gestionComercio.exception;

import org.gestionComercio.navigation.AppView;

public class ViewLoadException extends RuntimeException {

    public ViewLoadException(AppView view, Throwable cause) {
        super("Error al cargar la vista: " + view.name() +
                " (" + view.getFxml() + ")", cause);
    }

}