package org.gestionComercio.navigation;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.config.WindowConfig;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.List;

@Getter
@Component
@RequiredArgsConstructor
public class StageManager {

    /** Las vistas que se abren en el contenido del Dashboard heredan su hoja de estilos; una ventana nueva no. */
    private static final String STYLESHEET = "/org/gestionComercio/style/style.css";

    private final VentanaPrincipalLocal ventanaPrincipalLocal;

    private Stage primaryStage;

    /** Si la ventana muestra el Dashboard (redimensionable y con su estado recordado) y no el login. */
    private boolean ventanaPrincipalAbierta;

    /** Posición y tamaño sin maximizar: los que toma al restaurar y los que se recuerdan. */
    private Rectangle2D ubicacionNormal;

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;

        // Maximizada o minimizada, la ventana informa la ubicación de ese estado; se toma
        // al terminar cada cambio, cuando ya se sabe si quedó maximizada o minimizada.
        InvalidationListener registrar = obs -> Platform.runLater(this::registrarUbicacionNormal);
        primaryStage.xProperty().addListener(registrar);
        primaryStage.yProperty().addListener(registrar);
        primaryStage.widthProperty().addListener(registrar);
        primaryStage.heightProperty().addListener(registrar);
        primaryStage.maximizedProperty().addListener(registrar);
        primaryStage.iconifiedProperty().addListener(registrar);
    }

    public void showWindow(LoadedView loadedView) {

        WindowMode modo = loadedView.view().getWindowMode();

        if (modo != WindowMode.MAXIMIZED) {
            guardarEstadoVentana();
            ventanaPrincipalAbierta = false;
        }

        Scene scene = new Scene(loadedView.root());

        primaryStage.setScene(scene);
        primaryStage.setTitle(loadedView.title());

        switch (modo) {

            case AUTO_SIZE -> {
                // El login es de tamaño fijo, también al volver a él después de cerrar sesión.
                primaryStage.setMaximized(false);
                primaryStage.setMinWidth(0);
                primaryStage.setMinHeight(0);
                primaryStage.setResizable(false);
                primaryStage.sizeToScene();
                primaryStage.centerOnScreen();
            }

            case FIXED -> {
                primaryStage.setMaximized(false);
                primaryStage.setResizable(true);
                primaryStage.setMinWidth(WindowConfig.MIN_WIDTH);
                primaryStage.setMinHeight(WindowConfig.MIN_HEIGHT);
                primaryStage.setWidth(WindowConfig.WIDTH);
                primaryStage.setHeight(WindowConfig.HEIGHT);
                primaryStage.centerOnScreen();
            }

            case MAXIMIZED -> abrirVentanaPrincipal();
        }

        primaryStage.show();
    }

    /**
     * Guarda en esta PC cómo quedó la ventana principal, para abrirla igual la
     * próxima vez. Se llama al cerrar sesión y al salir del sistema.
     */
    public void guardarEstadoVentana() {

        if (!ventanaPrincipalAbierta || ubicacionNormal == null) {
            return;
        }

        ventanaPrincipalLocal.guardar(new EstadoVentana(
                primaryStage.isMaximized(),
                ubicacionNormal.getMinX(),
                ubicacionNormal.getMinY(),
                ubicacionNormal.getWidth(),
                ubicacionNormal.getHeight()));
    }

    /**
     * El contenido va en un panel con desplazamiento que ocupa todo el espacio
     * y sólo muestra las barras si la ventana es más chica que lo que la vista
     * necesita, para que no queden controles cortados.
     *
     * <p>El panel nunca toma el foco: algunas vistas escuchan sus atajos de
     * teclado en su raíz (por ejemplo, el punto de venta) y dejarían de
     * recibirlos si el foco quedara en el panel, por encima de ella.
     */
    public void showContent(LoadedView loadedView, StackPane contentPane) {

        ScrollPane contenido = new ScrollPane(loadedView.root()) {
            @Override
            public void requestFocus() {
                // El foco queda en los controles de la vista.
            }
        };
        contenido.setFocusTraversable(false);
        contenido.setFitToWidth(true);
        contenido.setFitToHeight(true);
        contenido.getStyleClass().add("content-scroll");

        contentPane.getChildren().setAll(contenido);
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

    /**
     * Ventana del Dashboard: redimensionable, sin bajar del tamaño mínimo, y
     * como se dejó la última vez en esta PC.
     */
    private void abrirVentanaPrincipal() {

        primaryStage.setResizable(true);
        primaryStage.setMinWidth(WindowConfig.MIN_WIDTH);
        primaryStage.setMinHeight(WindowConfig.MIN_HEIGHT);

        EstadoVentana estado = ventanaPrincipalLocal.leer().orElseGet(EstadoVentana::inicial);

        List<Rectangle2D> pantallas = Screen.getScreens().stream()
                .map(Screen::getVisualBounds)
                .toList();
        Rectangle2D ubicacion = estado.ubicacionEn(pantallas, Screen.getPrimary().getVisualBounds());

        // Primero la ubicación sin maximizar, que es la que toma al restaurar.
        primaryStage.setMaximized(false);
        primaryStage.setX(ubicacion.getMinX());
        primaryStage.setY(ubicacion.getMinY());
        primaryStage.setWidth(ubicacion.getWidth());
        primaryStage.setHeight(ubicacion.getHeight());
        primaryStage.setMaximized(estado.maximizada());

        ubicacionNormal = ubicacion;
        ventanaPrincipalAbierta = true;
    }

    private void registrarUbicacionNormal() {

        if (!ventanaPrincipalAbierta
                || !primaryStage.isShowing()
                || primaryStage.isMaximized()
                || primaryStage.isIconified()) {
            return;
        }

        ubicacionNormal = new Rectangle2D(
                primaryStage.getX(),
                primaryStage.getY(),
                primaryStage.getWidth(),
                primaryStage.getHeight());
    }

}
