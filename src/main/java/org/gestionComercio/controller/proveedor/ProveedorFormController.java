package org.gestionComercio.controller.proveedor;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.base.ErroresPorCampo;
import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.ProveedorService;
import org.gestionComercio.validation.ProveedorValidator;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class ProveedorFormController extends AbstractController implements DataReceiver<ProveedorDto> {

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

    private final ErroresPorCampo errores = new ErroresPorCampo();

    @Override
    protected void initializeComponents() {

        errores
                .registrar("razonSocial", txtRazonSocial, lblErrorRazonSocial, txtRazonSocial.textProperty())
                .registrar("cuit", txtCuit, lblErrorCuit, txtCuit.textProperty())
                .registrar("telefono", txtTelefono, lblErrorTelefono, txtTelefono.textProperty())
                .registrar("email", txtEmail, lblErrorEmail, txtEmail.textProperty())
                .registrar("direccion", txtDireccion, lblErrorDireccion, txtDireccion.textProperty())
                .registrar("observaciones", txtObservaciones, lblErrorObservaciones,
                        txtObservaciones.textProperty());

        lblTitulo.setText("Nuevo proveedor");
        txtRazonSocial.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnCancelar.setOnAction(e -> volver());

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

        errores.limpiar();

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
                errores.mostrar(ex.getValidationResult());
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

    /** Aviso inmediato al superar el largo máximo, sin esperar a guardar. */
    private void validarLargoObservaciones(String texto) {

        if (texto != null && texto.length() > ProveedorValidator.OBSERVACIONES_MAX) {
            errores.mostrar("observaciones",
                    "Las observaciones no pueden superar los " + ProveedorValidator.OBSERVACIONES_MAX
                            + " caracteres (tienen " + texto.length() + ").");
        }
    }

    private void volver() {
        navigator.navigate(AppView.PROVEEDOR_LIST);
    }
}
