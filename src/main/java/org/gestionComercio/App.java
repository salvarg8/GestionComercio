package org.gestionComercio;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.gestionComercio.config.SpringContext;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.navigation.StageManager;
import org.gestionComercio.navigation.View;
import org.gestionComercio.navigation.ViewLoader;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class App extends Application {

    private ConfigurableApplicationContext context;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        context = new SpringApplicationBuilder(App.class).run();
        SpringContext.setContext(context);
    }
    @Override
    public void start(Stage stage) {

        StageManager stageManager = SpringContext.getContext().getBean(StageManager.class);
        Navigator navigator = SpringContext.getContext().getBean(Navigator.class);

        stageManager.setPrimaryStage(stage);

        stage.setResizable(false);

        navigator.open(View.LOGIN);
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
        Platform.exit();
    }

    public ConfigurableApplicationContext getContext() {
        return context;
    }
}