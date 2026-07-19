package org.gestionComercio.navigation;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import lombok.Getter;
import org.gestionComercio.config.WindowConfig;
import org.springframework.boot.autoconfigure.web.reactive.WebSessionIdResolverAutoConfiguration;
import org.springframework.stereotype.Component;

@Getter
@Component
public class StageManager {

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

    public void showModal(LoadedView loadedView) {
        throw new UnsupportedOperationException(
                "La navegación modal aún no fue implementada.");
    }

}