package org.gestionComercio.controller.base;

public interface ViewController {

    /**
     * Se ejecuta una única vez cuando el FXML termina de cargarse.
     */
    default void onInit() {
    }

    /**
     * Se ejecuta cada vez que la vista se muestra.
     */
    default void onShow() {
    }

    /**
     * Se ejecuta cuando la vista deja de mostrarse.
     */
    default void onClose() {
    }

}