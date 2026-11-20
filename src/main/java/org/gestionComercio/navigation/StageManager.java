package org.gestionComercio.navigation;

import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;
import org.gestionComercio.config.WindowConfig;
import org.springframework.stereotype.Component;

import java.net.URL;

@Getter
@Component
public class StageManager {

    /** Las vistas que se abren en el contenido del Dashboard heredan su hoja de estilos; una ventana nueva no. */
    private static final String STYLESHEET = "/org/gestionComercio/style/style.css";

    private Stage primaryStage;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void showWindow(LoadedView loadedView) {

        Scene scene = new Scene(loadedView.root());

        primaryStage.setScene(scene);
        primaryStage.setTitle(loadedView.title());

        switch (loadedView.view().getWindowMode()) {

            case AUTO_SIZE -> {
                primaryStage.sizeToScene();
                primaryStage.setResizable(false);
                primaryStage.centerOnScreen();
            }

            case FIXED -> {
                primaryStage.setWidth(WindowConfig.WIDTH);
                primaryStage.setHeight(WindowConfig.HEIGHT);
                primaryStage.setMinWidth(WindowConfig.MIN_WIDTH);
                primaryStage.setMinHeight(WindowConfig.MIN_HEIGHT);
                primaryStage.centerOnScreen();
            }

            case MAXIMIZED -> {
                primaryStage.setMaximized(true);
            }
        }

        primaryStage.show();
    }

    public void showContent(LoadedView loadedView, StackPane contentPane) {
        contentPane.getChildren().setAll(loadedView.root());
    }

    /**
     * Muestra la vista en una ventana modal sobre la principal y espera a que
     * se cierre. El contenido va en un panel con desplazamiento por si no entra
     * en la pantalla.
     */
    public void showModal(LoadedView loadedView) {

        ScrollPane contenido = new ScrollPane(loadedView.root());
        contenido.setFitToWidth(true);

        Scene scene = new Scene(contenido);

        URL css = getClass().getResource(STYLESHEET);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        Stage modal = new Stage();
        modal.initModality(Modality.WINDOW_MODAL);
        modal.setTitle(loadedView.title());
        modal.setScene(scene);

        if (primaryStage != null) {
            modal.initOwner(primaryStage);
            modal.getIcons().setAll(primaryStage.getIcons());
        }

        modal.sizeToScene();

        double altoMaximo = Screen.getPrimary().getVisualBounds().getHeight() * 0.9;
        modal.setOnShown(e -> {
            if (modal.getHeight() > altoMaximo) {
                modal.setHeight(altoMaximo);
                modal.centerOnScreen();
            }
        });

        modal.showAndWait();
    }

}