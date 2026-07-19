package org.gestionComercio.navigation;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.exception.ViewLoadException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ViewLoader {

    private final ApplicationContext applicationContext;

    public LoadedView load(AppView view) {

        try {

            FXMLLoader loader =
                    new FXMLLoader(getClass().getResource(view.getFxml()));

            loader.setControllerFactory(applicationContext::getBean);

            Parent root = loader.load();

            return new LoadedView(
                    view,
                    root,
                    loader.getController()
            );

        } catch (IOException e) {
            throw new ViewLoadException(view, e);
        }
    }
}