package org.gestionComercio.util.dialog;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * Punto único para crear las ventanas emergentes de la aplicación con el mismo
 * estilo que la pantalla principal (ver style/dialogs.css).
 */
public final class Dialogs {

    private static final String STYLESHEET = "/org/gestionComercio/style/style.css";
    private static final String ICONO_APP = "/org/gestionComercio/images/Logo3.png";

    private static final double ANCHO_CONTENIDO = 380;

    public static final ButtonType ACEPTAR = new ButtonType("Aceptar", ButtonData.OK_DONE);
    public static final ButtonType CANCELAR = new ButtonType("Cancelar", ButtonData.CANCEL_CLOSE);

    public enum Tipo {

        INFORMACION("dialog-info", FontAwesomeSolid.INFO_CIRCLE, Alert.AlertType.INFORMATION),
        EXITO("dialog-success", FontAwesomeSolid.CHECK_CIRCLE, Alert.AlertType.INFORMATION),
        ADVERTENCIA("dialog-warning", FontAwesomeSolid.EXCLAMATION_TRIANGLE, Alert.AlertType.WARNING),
        ERROR("dialog-error", FontAwesomeSolid.TIMES_CIRCLE, Alert.AlertType.ERROR),
        CONFIRMACION("dialog-info", FontAwesomeSolid.QUESTION_CIRCLE, Alert.AlertType.CONFIRMATION),
        PELIGRO("dialog-error", FontAwesomeSolid.EXCLAMATION_TRIANGLE, Alert.AlertType.CONFIRMATION);

        private final String styleClass;
        private final Ikon icono;
        private final Alert.AlertType alertType;

        Tipo(String styleClass, Ikon icono, Alert.AlertType alertType) {
            this.styleClass = styleClass;
            this.icono = icono;
            this.alertType = alertType;
        }
    }

    private Dialogs() {
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    public static void informacion(String titulo, String mensaje) {
        mostrar(Tipo.INFORMACION, titulo, mensaje);
    }

    public static void exito(String titulo, String mensaje) {
        mostrar(Tipo.EXITO, titulo, mensaje);
    }

    public static void advertencia(String titulo, String mensaje) {
        mostrar(Tipo.ADVERTENCIA, titulo, mensaje);
    }

    public static void error(String titulo, String mensaje) {
        mostrar(Tipo.ERROR, titulo, mensaje);
    }

    /** Muestra una lista de problemas, uno por renglón (por ejemplo, errores de validación). */
    public static void lista(Tipo tipo, String titulo, String introduccion, List<String> items) {

        VBox cuerpo = new VBox(6);

        if (introduccion != null && !introduccion.isBlank()) {
            cuerpo.getChildren().add(mensaje(introduccion));
        }

        for (String item : items) {
            Label renglon = mensaje("•  " + item);
            renglon.getStyleClass().add("dialog-list-item");
            cuerpo.getChildren().add(renglon);
        }

        crear(tipo, titulo, cuerpo, ACEPTAR).showAndWait();
    }

    /**
     * Pide confirmación. Devuelve true si el usuario aceptó.
     *
     * @param textoAceptar texto del botón de aceptar (por ejemplo "Eliminar")
     * @param peligroso    true para acciones destructivas: botón rojo y "Cancelar"
     *                     como opción por defecto, así Enter no confirma por accidente
     */
    public static boolean confirmar(String titulo, String mensaje,
                                    String textoAceptar, boolean peligroso) {

        Optional<ButtonType> resultado =
                crearConfirmacion(titulo, mensaje, textoAceptar, peligroso).showAndWait();

        return resultado.isPresent()
                && resultado.get().getButtonData() == ButtonData.OK_DONE;
    }

    public static boolean confirmar(String titulo, String mensaje) {
        return confirmar(titulo, mensaje, "Aceptar", false);
    }

    /** Arma la ventana de confirmación sin mostrarla. */
    public static Alert crearConfirmacion(String titulo, String mensaje,
                                          String textoAceptar, boolean peligroso) {

        ButtonType aceptar = new ButtonType(textoAceptar, ButtonData.OK_DONE);

        Alert alert = crear(
                peligroso ? Tipo.PELIGRO : Tipo.CONFIRMACION,
                titulo,
                mensaje(mensaje),
                CANCELAR,
                aceptar);

        Button botonAceptar = (Button) alert.getDialogPane().lookupButton(aceptar);
        Button botonCancelar = (Button) alert.getDialogPane().lookupButton(CANCELAR);

        if (peligroso) {
            estilo(botonAceptar, "btn-danger");
            botonAceptar.setDefaultButton(false);
            botonCancelar.setDefaultButton(true);
        }

        return alert;
    }

    // ------------------------------------------------------------------
    // Construcción
    // ------------------------------------------------------------------

    private static void mostrar(Tipo tipo, String titulo, String mensaje) {
        crear(tipo, titulo, mensaje(mensaje), ACEPTAR).showAndWait();
    }

    /**
     * Crea el Alert ya estilizado, sin mostrarlo. Público para poder reutilizar
     * el estilo en diálogos con contenido propio.
     */
    public static Alert crear(Tipo tipo, String titulo, Node cuerpo, ButtonType... botones) {

        Alert alert = new Alert(tipo.alertType, "", botones);

        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setGraphic(null);

        DialogPane pane = alert.getDialogPane();

        pane.setContent(contenido(tipo, titulo, cuerpo));
        pane.getStyleClass().addAll("app-dialog", tipo.styleClass);
        pane.setMinHeight(Region.USE_PREF_SIZE);

        URL css = Dialogs.class.getResource(STYLESHEET);
        if (css != null) {
            pane.getStylesheets().add(css.toExternalForm());
        }

        estilizarBotones(pane);
        asignarVentanaPadre(alert);

        return alert;
    }

    private static Node contenido(Tipo tipo, String titulo, Node cuerpo) {

        FontIcon icono = new FontIcon(tipo.icono);
        icono.getStyleClass().add("dialog-icon");

        StackPane insignia = new StackPane(icono);
        insignia.getStyleClass().add("dialog-icon-badge");

        Label lblTitulo = new Label(titulo);
        lblTitulo.getStyleClass().add("dialog-title");
        lblTitulo.setWrapText(true);
        lblTitulo.setMaxWidth(ANCHO_CONTENIDO);

        VBox textos = new VBox(6, lblTitulo, cuerpo);
        HBox.setHgrow(textos, Priority.ALWAYS);

        HBox contenido = new HBox(16, insignia, textos);
        contenido.setAlignment(Pos.TOP_LEFT);
        contenido.getStyleClass().add("dialog-body");

        return contenido;
    }

    private static Label mensaje(String texto) {

        Label label = new Label(texto);
        label.getStyleClass().add("dialog-message");
        label.setWrapText(true);
        label.setMaxWidth(ANCHO_CONTENIDO);
        label.setPrefWidth(ANCHO_CONTENIDO);
        label.setMinHeight(Region.USE_PREF_SIZE);

        return label;
    }

    private static void estilizarBotones(DialogPane pane) {

        for (ButtonType tipo : pane.getButtonTypes()) {

            Node nodo = pane.lookupButton(tipo);

            if (nodo instanceof Button boton) {
                estilo(boton, tipo.getButtonData().isCancelButton()
                        ? "btn-secondary"
                        : "btn-primary");
            }
        }
    }

    private static void estilo(Button boton, String clase) {
        boton.getStyleClass().removeAll("btn-primary", "btn-secondary", "btn-danger");
        boton.getStyleClass().add(clase);
    }

    /**
     * Centra el diálogo sobre la ventana activa y le pone el ícono de la app,
     * así no aparece suelto en otra parte de la pantalla ni con el ícono de Java.
     */
    private static void asignarVentanaPadre(Alert alert) {

        Optional<Window> activa = Window.getWindows().stream()
                .filter(Window::isShowing)
                .filter(Window::isFocused)
                .findFirst()
                .or(() -> Window.getWindows().stream().filter(Window::isShowing).findFirst());

        activa.ifPresent(alert::initOwner);

        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();

        if (activa.isPresent() && activa.get() instanceof Stage padre && !padre.getIcons().isEmpty()) {
            stage.getIcons().setAll(padre.getIcons());
            return;
        }

        URL icono = Dialogs.class.getResource(ICONO_APP);
        if (icono != null) {
            stage.getIcons().setAll(new Image(icono.toExternalForm()));
        }
    }
}
