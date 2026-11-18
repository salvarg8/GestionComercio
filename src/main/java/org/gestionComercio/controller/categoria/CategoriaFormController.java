package org.gestionComercio.controller.categoria;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.base.ErroresPorCampo;
import org.gestionComercio.dto.categoria.CategoriaDto;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.CategoriaService;
import org.gestionComercio.validation.CategoriaValidator;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class CategoriaFormController extends AbstractController implements DataReceiver<CategoriaDto> {

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

    private final ErroresPorCampo errores = new ErroresPorCampo();

    @Override
    protected void initializeComponents() {

        errores
                .registrar("nombre", txtNombre, lblErrorNombre, txtNombre.textProperty())
                .registrar("descripcion", txtDescripcion, lblErrorDescripcion, txtDescripcion.textProperty());

        lblTitulo.setText("Nueva categoría");
        txtNombre.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnCancelar.setOnAction(e -> volver());

        txtDescripcion.textProperty().addListener((obs, o, n) -> validarLargoDescripcion(n));
    }

    @Override
    public void setData(CategoriaDto categoria) {

        categoriaActual = categoria;

        lblTitulo.setText("Editar categoría");
        txtNombre.setText(categoria.getNombre());
        txtDescripcion.setText(categoria.getDescripcion());
    }

    private void guardar() {

        errores.limpiar();

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
                errores.mostrar(ex.getValidationResult());
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

    /** Aviso inmediato al superar el largo máximo, sin esperar a guardar. */
    private void validarLargoDescripcion(String texto) {

        if (texto != null && texto.length() > CategoriaValidator.DESCRIPCION_MAX) {
            errores.mostrar("descripcion",
                    "La descripción no puede superar los " + CategoriaValidator.DESCRIPCION_MAX
                            + " caracteres (tiene " + texto.length() + ").");
        }
    }

    private void volver() {
        navigator.navigate(AppView.CATEGORIA_LIST);
    }
}
