package org.gestionComercio.controller.licencia;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.licencia.EstadoLicencia;
import org.gestionComercio.licencia.IdentificadorEquipo;
import org.gestionComercio.licencia.Licencia;
import org.gestionComercio.licencia.ModuloLicencia;
import org.gestionComercio.licencia.SituacionLicencia;
import org.gestionComercio.service.LicenciaService;
import org.gestionComercio.util.dialog.Dialogs;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Estado de la licencia, código de esta PC y activación de una licencia
 * nueva (LICENCIA_ADMINISTRAR). Disponible también en modo sólo consulta,
 * para poder activar la licencia que lo levanta.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class LicenciaController extends AbstractController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final LicenciaService licenciaService;

    private final CambiosLicencia cambiosLicencia;

    @FXML
    private Label lblEstado;

    @FXML
    private GridPane gridDatos;

    @FXML
    private Label lblCliente;

    @FXML
    private Label lblCuit;

    @FXML
    private Label lblModalidad;

    @FXML
    private Label lblModulos;

    @FXML
    private Label lblEquipos;

    @FXML
    private Label lblEmitida;

    @FXML
    private TextField txtCodigoEquipo;

    @FXML
    private Button btnCopiarCodigo;

    @FXML
    private TextArea txtClave;

    @FXML
    private Button btnCargarArchivo;

    @FXML
    private Button btnActivar;

    @Override
    protected void initializeComponents() {
        execute(() -> {
            txtCodigoEquipo.setText(licenciaService.codigoEquipo());
            mostrar(licenciaService.estado());
        });
    }

    @Override
    protected void initializeBindings() {
        btnActivar.disableProperty().bind(txtClave.textProperty().isEmpty());
    }

    @Override
    protected void initializeEvents() {
        btnCopiarCodigo.setOnAction(e -> copiarCodigo());
        btnCargarArchivo.setOnAction(e -> cargarArchivo());
        btnActivar.setOnAction(e -> activar());
    }

    private void mostrar(EstadoLicencia estado) {

        lblEstado.setText(estado.situacion().getDescripcion() + ". " + estado.mensaje());
        lblEstado.getStyleClass().setAll(estilo(estado));

        Licencia licencia = estado.licencia();
        visible(gridDatos, licencia != null);

        if (licencia == null) {
            return;
        }

        lblCliente.setText(licencia.cliente());
        lblCuit.setText(licencia.cuit());
        lblModalidad.setText(licencia.perpetua()
                ? "Pago único (sin vencimiento)"
                : "Abono, vigente hasta el " + FORMATO_FECHA.format(licencia.vencimiento()));
        lblModulos.setText(licencia.modulos().isEmpty() ? "Sólo el sistema de gestión"
                : Arrays.stream(ModuloLicencia.values())
                .filter(licencia::tieneModulo)
                .map(ModuloLicencia::getDescripcion)
                .collect(Collectors.joining(", ")));
        String estaPc = IdentificadorEquipo.normalizar(estado.codigoEquipo());
        lblEquipos.setText(licencia.equipos().size() + ": " + licencia.equipos().stream()
                .map(codigo -> IdentificadorEquipo.formatear(codigo) + (codigo.equals(estaPc) ? " (esta PC)" : ""))
                .collect(Collectors.joining(", ")));
        lblEmitida.setText(FORMATO_FECHA_HORA.format(licencia.emitida()));
    }

    private void copiarCodigo() {
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(txtCodigoEquipo.getText());
        Clipboard.getSystemClipboard().setContent(contenido);
        Dialogs.informacion("Código copiado", "El código de esta PC se copió al portapapeles.");
    }

    private void cargarArchivo() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Archivo de licencia");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Licencias (*.lic)", "*.lic"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));

        File archivo = chooser.showOpenDialog(txtClave.getScene().getWindow());

        if (archivo == null) {
            return;
        }

        try {
            txtClave.setText(Files.readString(archivo.toPath()));
        } catch (IOException e) {
            warning("No se pudo leer el archivo: " + e.getMessage());
        }
    }

    private void activar() {
        execute(() -> {
            EstadoLicencia estado = licenciaService.activar(txtClave.getText());
            txtClave.clear();
            mostrar(estado);
            cambiosLicencia.publicar(estado);
            Dialogs.exito("Licencia activada", estado.mensaje()
                    + "\n\nVale para todas las PC incluidas en la licencia. Los cambios se aplican al "
                    + "volver a iniciar sesión.");
        });
    }

    private static String estilo(EstadoLicencia estado) {
        if (!estado.permiteOperar()) {
            return "form-error";
        }
        return estado.situacion() == SituacionLicencia.ACTIVA ? "form-success" : "form-warning";
    }

    private static void visible(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

}
