package org.gestionComercio.navigation;

import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Navigator {

    private final StageManager stageManager;
    private final ViewLoader viewLoader;

    private StackPane contentPane;

    public void setContentPane(StackPane contentPane) {
        this.contentPane = contentPane;
    }

    public void navigate(AppView view) {
        navigate(view, null);
    }

    public void navigate(AppView view, Object data) {

        LoadedView loadedView = viewLoader.load(view);

        if (data != null && loadedView.controller() instanceof DataReceiver receiver) {
            receiver.setData(data);
        }

        show(loadedView);
    }

    private void show(LoadedView loadedView) {

        switch (loadedView.navigationType()) {

            case WINDOW -> stageManager.showWindow(loadedView);

            case CONTENT -> {

                if (contentPane == null) {
                    throw new IllegalStateException(
                            "El contenedor principal del Dashboard no fue inicializado.");
                }

                stageManager.showContent(loadedView, contentPane);
            }

            case MODAL -> stageManager.showModal(loadedView);
        }
    }
}