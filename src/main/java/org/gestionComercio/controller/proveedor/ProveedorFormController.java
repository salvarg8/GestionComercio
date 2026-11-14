package org.gestionComercio.controller.proveedor;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.ProveedorService;
import org.gestionComercio.util.dialog.ValidationDialog;
import org.gestionComercio.validation.ProveedorValidator;
import org.gestionComercio.validation.ValidationError;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class ProveedorFormController extends AbstractController implements DataReceiver<ProveedorDto> {

    private static final String CLASE_INVALIDO = "field-invalid";

    private final ProveedorService proveedorService;
    private final Navigator navigator;

    @FXML
    private Label lblTitulo;

    @FXML
    private TextField txtRazonSocial;

    @FXML
    private Label lblErrorRazonSocial;

    @FXML
    private TextField txtCuit;

    @FXML
    private Label lblErrorCuit;

    @FXML
    private TextField txtTelefono;

    @FXML
    private Label lblErrorTelefono;

    @FXML
    private TextField txtEmail;

    @FXML
    private Label lblErrorEmail;

    @FXML
    private TextField txtDireccion;

    @FXML
    private Label lblErrorDireccion;

    @FXML
    private TextArea txtObservaciones;

    @FXML
    private Label lblErrorObservaciones;

    @FXML
    private Button btnGuardar;

    @FXML
    private Button btnCancelar;

    /** null = alta; distinto de null = edición. */
    private ProveedorDto proveedorActual;

    /** Campo del DTO → control y etiqueta donde se muestra su error. */
    private final Map<String, CampoConError> campos = new LinkedHashMap<>();

    private record CampoConError(TextInputControl control, Label lblError) {
    }

    @Override
    protected void initializeComponents() {

        campos.put("razonSocial", new CampoConError(txtRazonSocial, lblErrorRazonSocial));
        campos.put("cuit", new CampoConError(txtCuit, lblErrorCuit));
        campos.put("telefono", new CampoConError(txtTelefono, lblErrorTelefono));
        campos.put("email", new CampoConError(txtEmail, lblErrorEmail));
        campos.put("direccion", new CampoConError(txtDireccion, lblErrorDireccion));
        campos.put("observaciones", new CampoConError(txtObservaciones, lblErrorObservaciones));

        lblTitulo.setText("Nuevo proveedor");
        txtRazonSocial.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnCancelar.setOnAction(e -> volver());

        // El error de un campo se borra en cuanto el usuario lo corrige.
        campos.forEach((campo, control) ->
                control.control().textProperty().addListener((obs, o, n) -> limpiarError(campo)));

        txtObservaciones.textProperty().addListener((obs, o, n) -> validarLargoObservaciones(n));
    }

    @Override
    public void setData(ProveedorDto proveedor) {

        proveedorActual = proveedor;

        lblTitulo.setText("Editar proveedor");
        txtRazonSocial.setText(proveedor.getRazonSocial());
        txtCuit.setText(proveedor.getCuitFormateado());
        txtTelefono.setText(proveedor.getTelefono());
        txtEmail.setText(proveedor.getEmail());
        txtDireccion.setText(proveedor.getDireccion());
        txtObservaciones.setText(proveedor.getObservaciones());
    }

    private void guardar() {

        limpiarErrores();

        execute(() -> {

            ProveedorDto dto = construirDto();

            try {

                if (proveedorActual == null) {
                    proveedorService.save(dto);
                    success("Proveedor creado correctamente.");
                } else {
                    proveedorService.update(dto);
                    success("Proveedor actualizado correctamente.");
                }

            } catch (ValidationException ex) {
                mostrarErrores(ex.getValidationResult());
                return;
            }

            volver();
        });
    }

    private ProveedorDto construirDto() {

        return ProveedorDto.builder()
                .id(proveedorActual != null ? proveedorActual.getId() : null)
                .razonSocial(txtRazonSocial.getText())
                .cuit(txtCuit.getText())
                .telefono(txtTelefono.getText())
                .email(txtEmail.getText())
                .direccion(txtDireccion.getText())
                .observaciones(txtObservaciones.getText())
                .build();
    }

    /**
     * Muestra cada error debajo de su campo. Si alguno no corresponde a un campo
     * del formulario, se muestran todos en la ventana de validación habitual.
     */
    private void mostrarErrores(ValidationResult result) {

        boolean todosUbicados = true;

        for (ValidationError error : result.getErrors()) {

            CampoConError campo = campos.get(error.getField());

            if (campo == null) {
                todosUbicados = false;
                continue;
            }

            campo.lblError().setText(error.getMessage());
            mostrar(campo.lblError(), true);

            Control control = campo.control();
            if (!control.getStyleClass().contains(CLASE_INVALIDO)) {
                control.getStyleClass().add(CLASE_INVALIDO);
            }
        }

        if (!todosUbicados) {
            ValidationDialog.show(result);
        }

        result.getErrors().stream()
                .map(error -> campos.get(error.getField()))
                .filter(campo -> campo != null)
                .findFirst()
                .ifPresent(campo -> campo.control().requestFocus());
    }

    /** Aviso inmediato al superar el largo máximo, sin esperar a guardar. */
    private void validarLargoObservaciones(String texto) {

        if (texto != null && texto.length() > ProveedorValidator.OBSERVACIONES_MAX) {
            ValidationResult result = new ValidationResult();
            result.addError("observaciones",
                    "Las observaciones no pueden superar los " + ProveedorValidator.OBSERVACIONES_MAX
                            + " caracteres (tienen " + texto.length() + ").");
            mostrarErrores(result);
        }
    }

    private void limpiarErrores() {
        campos.keySet().forEach(this::limpiarError);
    }

    private void limpiarError(String field) {

        CampoConError campo = campos.get(field);

        if (campo == null) {
            return;
        }

        campo.lblError().setText("");
        mostrar(campo.lblError(), false);
        campo.control().getStyleClass().remove(CLASE_INVALIDO);
    }

    private void mostrar(Label label, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
    }

    private void volver() {
        navigator.navigate(AppView.PROVEEDOR_LIST);
    }
}
