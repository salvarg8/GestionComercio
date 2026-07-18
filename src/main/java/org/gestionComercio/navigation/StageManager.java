package org.gestionComercio.navigation;

import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Getter
@Component
public class StageManager {

    private Stage primaryStage;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setScene(Scene scene) {
        primaryStage.setScene(scene);
    }

    public void setTitle(String titulo) {
        primaryStage.setTitle(titulo);
    }

    public void show() {
        primaryStage.show();
    }

}