package org.gestionComercio.exception;

import org.gestionComercio.util.dialog.Dialogs;

public class ExceptionDialog {

    public static void warning(String message) {
        Dialogs.advertencia("Atención", message);
    }

    public static void error(String message) {
        Dialogs.error("Error", message);
    }

    public static void information(String message) {
        Dialogs.informacion("Información", message);
    }

}
