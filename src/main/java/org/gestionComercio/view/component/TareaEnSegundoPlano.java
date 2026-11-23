package org.gestionComercio.view.component;

import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.util.dialog.Dialogs;
import org.gestionComercio.util.dialog.ValidationDialog;

import java.net.URL;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Ejecuta un trabajo lento (por ejemplo, una llamada a ARCA) fuera del hilo
 * de la interfaz. Mientras corre muestra una ventana modal con el mensaje,
 * que bloquea la aplicación y no se puede cerrar; al terminar llama a
 * {@code alTerminar} en el hilo de la interfaz. Los errores se muestran como
 * en {@code AbstractController.execute}.
 */
public final class TareaEnSegundoPlano {

    private static final String STYLESHEET = "/org/gestionComercio/style/style.css";

    private TareaEnSegundoPlano() {
    }

    public static <T> void ejecutar(String mensaje, Callable<T> trabajo, Consumer<T> alTerminar) {
        ejecutar(mensaje, trabajo, alTerminar, TareaEnSegundoPlano::mostrarError);
    }

    /**
     * @param alFallar recibe la excepción del trabajo, en el hilo de la interfaz
     */
    public static <T> void ejecutar(String mensaje, Callable<T> trabajo, Consumer<T> alTerminar,
                                    Consumer<Throwable> alFallar) {

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabajo.call();
            }
        };

        Stage espera = ventanaDeEspera(mensaje);

        task.setOnSucceeded(e -> {
            espera.hide();
            alTerminar.accept(task.getValue());
        });

        task.setOnFailed(e -> {
            espera.hide();
            alFallar.accept(task.getException());
        });

        Thread hilo = new Thread(task, "tarea-en-segundo-plano");
        hilo.setDaemon(true);
        hilo.start();

        // Los eventos de fin de la tarea llegan por la cola de la interfaz, siempre después de este show.
        espera.show();
    }

    /** Muestra el error como lo hace {@code AbstractController.execute}. */
    public static void mostrarError(Throwable error) {

        if (error instanceof ValidationException ex) {
            ValidationDialog.show(ex.getValidationResult());
            return;
        }

        if (error instanceof BusinessException) {
            Dialogs.advertencia("No se pudo completar la operación", error.getMessage());
            return;
        }

        Dialogs.error("Ocurrió un error inesperado", "Si el problema persiste, comuníquese con soporte.");
    }

    private static Stage ventanaDeEspera(String mensaje) {

        ProgressIndicator progreso = new ProgressIndicator();
        progreso.setPrefSize(28, 28);

        Label texto = new Label(mensaje);
        texto.setWrapText(true);
        texto.setMaxWidth(340);

        HBox contenido = new HBox(14, progreso, texto);
        contenido.setAlignment(Pos.CENTER_LEFT);
        contenido.setPadding(new Insets(20, 24, 20, 24));
        contenido.setStyle("-fx-background-color: white; -fx-border-color: #D1D5DB; -fx-border-radius: 8; "
                + "-fx-background-radius: 8;");

        Scene escena = new Scene(contenido);
        URL css = TareaEnSegundoPlano.class.getResource(STYLESHEET);
        if (css != null) {
            escena.getStylesheets().add(css.toExternalForm());
        }

        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(escena);
        stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, WindowEvent::consume);

        Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(Window::isFocused)
                .findFirst()
                .or(() -> Window.getWindows().stream().filter(Window::isShowing).findFirst())
                .ifPresent(stage::initOwner);

        return stage;
    }

}
