package org.gestionComercio.exception;

import javafx.scene.control.Alert;

public class ExceptionDialog {

    public static void warning(String message){

        Alert alert = new Alert(Alert.AlertType.WARNING);

        alert.setHeaderText(null);

        alert.setContentText(message);

        alert.showAndWait();

    }

    public static void error(String message){

        Alert alert = new Alert(Alert.AlertType.ERROR);

        alert.setHeaderText(null);

        alert.setContentText(message);

        alert.showAndWait();

    }

    public static void information(String message){

        Alert alert = new Alert(Alert.AlertType.INFORMATION);

        alert.setHeaderText(null);

        alert.setContentText(message);

        alert.showAndWait();

    }

}