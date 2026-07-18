package org.gestionComercio.navigation;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Navigator {

    private final StageManager stageManager;

    /**
     * Contenedor central del Dashboard.
     */
    private StackPane contentPane;

    /**
     * Se registra una única vez cuando se carga el Dashboard.
     */
    public void setContentPane(StackPane contentPane) {
        this.contentPane = contentPane;
    }

    /**
     * Cambia la escena completa.
     * Ejemplo:
     * Login -> Dashboard
     */
    public void open(View view) {

        Parent root = ViewLoader.load(view.getFxml());

        Scene scene = new Scene(root);

        stageManager.setScene(scene);
        stageManager.setTitle(view.getTitulo());
        stageManager.show();
    }

    /**
     * Cambia únicamente el contenido del Dashboard.
     */
    public void show(View view) {

        if (contentPane == null) {
            throw new IllegalStateException(
                    "El contenedor principal del Dashboard no fue inicializado.");
        }

        Parent root = ViewLoader.load(view.getFxml());

        contentPane.getChildren().setAll(root);
    }

}