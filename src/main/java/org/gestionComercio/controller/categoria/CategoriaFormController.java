package org.gestionComercio.controller.categoria;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.categoria.CategoriaDto;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.CategoriaService;
import org.gestionComercio.util.dialog.ValidationDialog;
import org.gestionComercio.validation.ValidationError;
import org.gestionComercio.validation.ValidationResult;
import org.gestionComercio.validation.CategoriaValidator;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class CategoriaFormController extends AbstractController implements DataReceiver<CategoriaDto> {

    private static final String CLASE_INVALIDO = "field-invalid";

    private final CategoriaService categoriaService;
    private final Navigator navigator;

    @FXML
    private Label lblTitulo;

    @FXML
    private TextField txtNombre;

    @FXML
    private Label lblErrorNombre;

    @FXML
    private TextArea txtDescripcion;

    @FXML
    private Label lblErrorDescripcion;

    @FXML
    private Button btnGuardar;

    @FXML
    private Button btnCancelar;

    /** null = alta; distinto de null = edición. */
    private CategoriaDto categoriaActual;

    /** Campo del DTO → control y etiqueta donde se muestra su error. */
    private final Map<String, CampoConError> campos = new LinkedHashMap<>();

    private record CampoConError(Control control, Label lblError) {
    }

    @Override
    protected void initializeComponents() {

        campos.put("nombre", new CampoConError(txtNombre, lblErrorNombre));
        campos.put("descripcion", new CampoConError(txtDescripcion, lblErrorDescripcion));

        lblTitulo.setText("Nueva categoría");
        txtNombre.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnCancelar.setOnAction(e -> volver());

        // El error de un campo se borra en cuanto el usuario lo corrige.
        txtNombre.textProperty().addListener((obs, o, n) -> limpiarError("nombre"));
        txtDescripcion.textProperty().addListener((obs, o, n) -> {
            limpiarError("descripcion");
            validarLargoDescripcion(n);
        });
    }

    @Override
    public void setData(CategoriaDto categoria) {

        categoriaActual = categoria;

        lblTitulo.setText("Editar categoría");
        txtNombre.setText(categoria.getNombre());
        txtDescripcion.setText(categoria.getDescripcion());
    }

    private void guardar() {

        limpiarErrores();

        execute(() -> {

            CategoriaDto dto = construirDto();

            try {

                if (categoriaActual == null) {
                    categoriaService.save(dto);
                    success("Categoría creada correctamente.");
                } else {
                    categoriaService.update(dto);
                    success("Categoría actualizada correctamente.");
                }

            } catch (ValidationException ex) {
                mostrarErrores(ex.getValidationResult());
                return;
            }

            volver();
        });
    }

    private CategoriaDto construirDto() {

        return CategoriaDto.builder()
                .id(categoriaActual != null ? categoriaActual.getId() : null)
                .nombre(txtNombre.getText())
                .descripcion(txtDescripcion.getText())
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

            if (!campo.control().getStyleClass().contains(CLASE_INVALIDO)) {
                campo.control().getStyleClass().add(CLASE_INVALIDO);
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
    private void validarLargoDescripcion(String texto) {

        if (texto != null && texto.length() > CategoriaValidator.DESCRIPCION_MAX) {
            ValidationResult result = new ValidationResult();
            result.addError("descripcion",
                    "La descripción no puede superar los " + CategoriaValidator.DESCRIPCION_MAX
                            + " caracteres (tiene " + texto.length() + ").");
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
        navigator.navigate(AppView.CATEGORIA_LIST);
    }
}
