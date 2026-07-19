package org.gestionComercio;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.gestionComercio.config.SpringContext;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.navigation.StageManager;
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
    public void start(Stage primaryStage) {

        StageManager stageManager = context.getBean(StageManager.class);
        Navigator navigator = context.getBean(Navigator.class);

        stageManager.setPrimaryStage(primaryStage);

        primaryStage.setResizable(false);

        navigator.navigate(AppView.LOGIN);
    }

    @Override
    public void stop() {

        if (context != null) {
            context.close();
        }

        Platform.exit();
    }
}