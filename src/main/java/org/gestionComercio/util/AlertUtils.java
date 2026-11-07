package org.gestionComercio.util;

import org.gestionComercio.util.dialog.Dialogs;

/**
 * Atajos para las ventanas emergentes más comunes. Todas usan el estilo de
 * {@link Dialogs}.
 */
public final class AlertUtils {

    private AlertUtils() {
    }

    public static void information(String message) {
        Dialogs.informacion("Información", message);
    }

    public static void success(String message) {
        Dialogs.exito("Operación realizada", message);
    }

    public static void warning(String title, String message) {
        Dialogs.advertencia(title, message);
    }

    public static void error(String message) {
        Dialogs.error("Error", message);
    }

    public static boolean confirm(String title, String message) {
        return Dialogs.confirmar(title, message);
    }

}
