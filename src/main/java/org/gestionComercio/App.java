package org.gestionComercio;

import javafx.application.Application;
import javafx.application.HostServices;
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
        // HostServices abre archivos con la aplicación del sistema (por ejemplo,
        // los comprobantes en PDF). Spring corre en modo headless, sin java.awt.Desktop.
        HostServices hostServices = getHostServices();

        context = new SpringApplicationBuilder(App.class)
                .initializers(ctx -> ctx.getBeanFactory().registerSingleton("hostServices", hostServices))
                .run();
        SpringContext.setContext(context);
    }

    @Override
    public void start(Stage primaryStage) {

        StageManager stageManager = context.getBean(StageManager.class);
        Navigator navigator = context.getBean(Navigator.class);

        stageManager.setPrimaryStage(primaryStage);

        primaryStage.setResizable(false);

        var icono = App.class.getResource("/org/gestionComercio/images/Logo3.png");
        if (icono != null) {
            primaryStage.getIcons().add(new javafx.scene.image.Image(icono.toExternalForm()));
        }

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