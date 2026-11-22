package org.gestionComercio.controller.backup;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.backup.ArchivosBackup;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.backup.ArchivoBackup;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.BackupService;
import org.gestionComercio.util.dialog.Dialogs;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Crear y restaurar copias de seguridad. Cada sección se muestra según
 * BACKUP_CREAR y BACKUP_RESTAURAR. Las operaciones corren en segundo plano;
 * mientras tanto la ventana queda bloqueada y no se puede cerrar.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class BackupController extends AbstractController {

    /** Última carpeta elegida para las copias manuales (se recuerda en esta PC). */
    private static final String PREFERENCIA_CARPETA = "backup.carpeta";

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final BackupService backupService;
    private final SesionUsuario sesionUsuario;

    @FXML
    private HBox panelProgreso;

    @FXML
    private Label lblProgreso;

    @FXML
    private VBox panelCrear;

    @FXML
    private TextField txtCarpeta;

    @FXML
    private Button btnElegirCarpeta;

    @FXML
    private Button btnCrear;

    @FXML
    private Separator sepRestaurar;

    @FXML
    private VBox panelRestaurar;

    @FXML
    private TextField txtArchivo;

    @FXML
    private Button btnElegirArchivo;

    @FXML
    private Button btnRestaurar;

    @FXML
    private Label lblAutomaticas;

    @FXML
    private TableView<ArchivoBackup> tblAutomaticas;

    @FXML
    private TableColumn<ArchivoBackup, String> colFecha;

    @FXML
    private TableColumn<ArchivoBackup, String> colTipo;

    @FXML
    private TableColumn<ArchivoBackup, String> colArchivo;

    @FXML
    private TableColumn<ArchivoBackup, String> colTamanio;

    @FXML
    private Button btnActualizar;

    @FXML
    private Button btnUsarSeleccionada;

    private final Preferences preferencias = Preferences.userNodeForPackage(BackupController.class);

    @Override
    protected void initializeComponents() {

        boolean puedeCrear = sesionUsuario.tienePermiso(PermisoCodigo.BACKUP_CREAR);
        boolean puedeRestaurar = sesionUsuario.tienePermiso(PermisoCodigo.BACKUP_RESTAURAR);

        mostrar(panelCrear, puedeCrear);
        mostrar(sepRestaurar, puedeCrear && puedeRestaurar);
        mostrar(panelRestaurar, puedeRestaurar);
        mostrar(btnUsarSeleccionada, puedeRestaurar);

        String carpeta = preferencias.get(PREFERENCIA_CARPETA, null);
        if (carpeta != null && Files.isDirectory(Path.of(carpeta))) {
            txtCarpeta.setText(carpeta);
        }

        colFecha.setCellValueFactory(c -> new SimpleStringProperty(FORMATO_FECHA.format(c.getValue().fecha())));
        colTipo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().antesDeRestaurar() ? "Antes de restaurar" : "Automática"));
        colArchivo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().nombre()));
        colTamanio.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().tamanioLegible()));

        lblAutomaticas.setText(descripcionAutomaticas());

        cargarAutomaticas();
    }

    @Override
    protected void initializeBindings() {
        btnUsarSeleccionada.disableProperty().bind(tblAutomaticas.getSelectionModel().selectedItemProperty().isNull());
    }

    @Override
    protected void initializeEvents() {

        btnElegirCarpeta.setOnAction(e -> elegirCarpeta());
        btnCrear.setOnAction(e -> crear());
        btnElegirArchivo.setOnAction(e -> elegirArchivo());
        btnRestaurar.setOnAction(e -> restaurar());
        btnActualizar.setOnAction(e -> cargarAutomaticas());
        btnUsarSeleccionada.setOnAction(e -> {
            ArchivoBackup seleccionada = tblAutomaticas.getSelectionModel().getSelectedItem();
            if (seleccionada != null) {
                txtArchivo.setText(seleccionada.ruta().toAbsolutePath().toString());
            }
        });
    }

    private String descripcionAutomaticas() {

        String carpeta = backupService.getCarpetaAutomatica().toString();

        if (backupService.isAutomaticoHabilitado()) {
            return "Esta PC hace una copia por día y otra después de cada cierre de caja, y conserva las últimas "
                    + backupService.getRetencion() + ". Carpeta: " + carpeta;
        }

        return "Esta PC no hace copias automáticas (las hace la PC servidor). Aquí se listan las copias "
                + "de la carpeta " + carpeta + ", incluidas las que se guardan antes de cada restauración.";
    }

    private void cargarAutomaticas() {
        execute(() -> tblAutomaticas.getItems().setAll(backupService.listarBackupsAutomaticos()));
    }

    // ------------------------------------------------------------------
    // Crear
    // ------------------------------------------------------------------

    private void elegirCarpeta() {

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Carpeta para la copia de seguridad");

        File actual = txtCarpeta.getText() == null || txtCarpeta.getText().isBlank()
                ? null : new File(txtCarpeta.getText());
        if (actual != null && actual.isDirectory()) {
            chooser.setInitialDirectory(actual);
        }

        File carpeta = chooser.showDialog(ventana());

        if (carpeta != null) {
            txtCarpeta.setText(carpeta.getAbsolutePath());
            preferencias.put(PREFERENCIA_CARPETA, carpeta.getAbsolutePath());
        }
    }

    private void crear() {

        Path carpeta = txtCarpeta.getText() == null || txtCarpeta.getText().isBlank()
                ? null : Path.of(txtCarpeta.getText());

        enSegundoPlano("Creando la copia de seguridad…", () -> backupService.crearBackup(carpeta), archivo -> {
            cargarAutomaticas();
            Dialogs.exito("Copia de seguridad creada", "Se guardó en:\n" + archivo
                    + "\n\nRecomendación: guarde también una copia fuera de esta PC, por ejemplo "
                    + "en un pendrive o en una carpeta sincronizada con la nube.");
        });
    }

    // ------------------------------------------------------------------
    // Restaurar
    // ------------------------------------------------------------------

    private void elegirArchivo() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Copia de seguridad a restaurar");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Copias de seguridad (*" + ArchivosBackup.EXTENSION + ")",
                        "*" + ArchivosBackup.EXTENSION),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));

        File carpetaInicial = backupService.getCarpetaAutomatica().toFile();
        if (carpetaInicial.isDirectory()) {
            chooser.setInitialDirectory(carpetaInicial);
        }

        File archivo = chooser.showOpenDialog(ventana());

        if (archivo != null) {
            txtArchivo.setText(archivo.getAbsolutePath());
        }
    }

    private void restaurar() {

        if (txtArchivo.getText() == null || txtArchivo.getText().isBlank()) {
            warning("Elija la copia de seguridad a restaurar.");
            return;
        }

        Path archivo = Path.of(txtArchivo.getText());

        boolean confirmado = Dialogs.confirmar("¿Restaurar la copia de seguridad?",
                "Se va a reemplazar TODA la información de la base de datos por la de la copia \""
                        + archivo.getFileName() + "\". Lo cargado después de esa copia se pierde.\n\n"
                        + "Antes se guarda automáticamente una copia de la base actual. Las demás PC se "
                        + "desconectan y, al terminar, hay que volver a abrir la aplicación en todas.",
                "Restaurar", true);

        if (!confirmado) {
            return;
        }

        enSegundoPlano("Restaurando la copia de seguridad. No apague la PC ni cierre la aplicación…",
                () -> backupService.restaurar(archivo),
                copiaPrevia -> {
                    Dialogs.exito("Restauración terminada",
                            "Se restauró la copia \"" + archivo.getFileName() + "\".\n\n"
                                    + "La base anterior se guardó en:\n" + copiaPrevia + "\n\n"
                                    + "La aplicación se va a cerrar. Vuelva a abrirla en esta y en las demás PC.");
                    Platform.exit();
                });
    }

    // ------------------------------------------------------------------
    // Segundo plano
    // ------------------------------------------------------------------

    /**
     * Ejecuta el trabajo fuera del hilo de la interfaz. Mientras corre, la
     * ventana queda deshabilitada y no se puede cerrar (cortar una restauración
     * a la mitad dejaría la base sin terminar de restaurar).
     */
    private <T> void enSegundoPlano(String mensaje, Callable<T> trabajo, Consumer<T> alTerminar) {

        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabajo.call();
            }
        };

        Window ventana = ventana();
        Node raiz = ventana.getScene().getRoot();
        var cierreAnterior = ventana.getOnCloseRequest();

        lblProgreso.setText(mensaje);
        mostrar(panelProgreso, true);
        raiz.setDisable(true);
        ventana.setOnCloseRequest(WindowEvent::consume);

        Runnable restaurarVentana = () -> {
            mostrar(panelProgreso, false);
            raiz.setDisable(false);
            ventana.setOnCloseRequest(cierreAnterior);
        };

        task.setOnSucceeded(e -> {
            restaurarVentana.run();
            alTerminar.accept(task.getValue());
        });

        task.setOnFailed(e -> {
            restaurarVentana.run();
            mostrarError(task.getException());
        });

        Thread hilo = new Thread(task, "copia-de-seguridad");
        hilo.start();
    }

    private void mostrarError(Throwable error) {

        if (error instanceof BusinessException) {
            Dialogs.advertencia("No se pudo completar la operación", error.getMessage());
            return;
        }

        Dialogs.error("Ocurrió un error inesperado",
                "Si el problema persiste, comuníquese con soporte.");
    }

    private Window ventana() {
        return txtCarpeta.getScene().getWindow();
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }
}
