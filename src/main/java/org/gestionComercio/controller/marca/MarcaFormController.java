package org.gestionComercio.controller.marca;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.base.ErroresPorCampo;
import org.gestionComercio.dto.marca.MarcaDto;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.MarcaService;
import org.gestionComercio.validation.MarcaValidator;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class MarcaFormController extends AbstractController implements DataReceiver<MarcaDto> {

    private final MarcaService marcaService;
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
    private MarcaDto marcaActual;

    private final ErroresPorCampo errores = new ErroresPorCampo();

    @Override
    protected void initializeComponents() {

        errores
                .registrar("nombre", txtNombre, lblErrorNombre, txtNombre.textProperty())
                .registrar("descripcion", txtDescripcion, lblErrorDescripcion, txtDescripcion.textProperty());

        lblTitulo.setText("Nueva marca");
        txtNombre.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnCancelar.setOnAction(e -> volver());

        txtDescripcion.textProperty().addListener((obs, o, n) -> validarLargoDescripcion(n));
    }

    @Override
    public void setData(MarcaDto marca) {

        marcaActual = marca;

        lblTitulo.setText("Editar marca");
        txtNombre.setText(marca.getNombre());
        txtDescripcion.setText(marca.getDescripcion());
    }

    private void guardar() {

        errores.limpiar();

        execute(() -> {

            MarcaDto dto = construirDto();

            try {

                if (marcaActual == null) {
                    marcaService.save(dto);
                    success("Marca creada correctamente.");
                } else {
                    marcaService.update(dto);
                    success("Marca actualizada correctamente.");
                }

            } catch (ValidationException ex) {
                errores.mostrar(ex.getValidationResult());
                return;
            }

            volver();
        });
    }

    private MarcaDto construirDto() {

        return MarcaDto.builder()
                .id(marcaActual != null ? marcaActual.getId() : null)
                .nombre(txtNombre.getText())
                .descripcion(txtDescripcion.getText())
                .build();
    }

    /** Aviso inmediato al superar el largo máximo, sin esperar a guardar. */
    private void validarLargoDescripcion(String texto) {

        if (texto != null && texto.length() > MarcaValidator.DESCRIPCION_MAX) {
            errores.mostrar("descripcion",
                    "La descripción no puede superar los " + MarcaValidator.DESCRIPCION_MAX
                            + " caracteres (tiene " + texto.length() + ").");
        }
    }

    private void volver() {
        navigator.navigate(AppView.MARCA_LIST);
    }
}
