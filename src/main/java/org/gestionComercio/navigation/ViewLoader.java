package org.gestionComercio.navigation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.gestionComercio.config.SpringContext;

import java.io.IOException;

public class ViewLoader {

    private ViewLoader() {
    }

    public static Parent load(String fxml) {

        try {

            FXMLLoader loader = new FXMLLoader(ViewLoader.class.getResource(fxml));

            loader.setControllerFactory(SpringContext.getContext()::getBean);

            return loader.load();

        } catch (IOException e) {
            throw new RuntimeException("Error al cargar el FXML: " + fxml, e);
        }
    }
}