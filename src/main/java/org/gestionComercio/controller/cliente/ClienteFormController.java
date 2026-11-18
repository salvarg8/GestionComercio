package org.gestionComercio.controller.cliente;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.base.ErroresPorCampo;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.ClienteService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class ClienteFormController extends AbstractController implements DataReceiver<ClienteDto> {

    private final ClienteService clienteService;
    private final Navigator navigator;

    @FXML
    private Label lblTitulo;

    @FXML
    private ComboBox<TipoDocumento> cmbTipoDocumento;

    @FXML
    private Label lblErrorTipoDocumento;

    @FXML
    private TextField txtNumeroDocumento;

    @FXML
    private Label lblErrorNumeroDocumento;

    @FXML
    private Label lblNombre;

    @FXML
    private TextField txtNombre;

    @FXML
    private Label lblErrorNombre;

    @FXML
    private Label lblApellido;

    @FXML
    private TextField txtApellido;

    @FXML
    private Label lblErrorApellido;

    @FXML
    private ComboBox<CondicionIVA> cmbCondicionIva;

    @FXML
    private Label lblErrorCondicionIva;

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
    private Button btnGuardar;

    @FXML
    private Button btnCancelar;

    /** null = alta; distinto de null = edición. */
    private ClienteDto clienteActual;

    private final ErroresPorCampo errores = new ErroresPorCampo();

    @Override
    protected void initializeComponents() {

        cmbTipoDocumento.getItems().setAll(TipoDocumento.values());
        cmbTipoDocumento.setValue(TipoDocumento.DNI);

        cmbCondicionIva.getItems().setAll(CondicionIVA.values());
        cmbCondicionIva.setValue(CondicionIVA.CONSUMIDOR_FINAL);

        errores
                .registrar("tipoDocumento", cmbTipoDocumento, lblErrorTipoDocumento,
                        cmbTipoDocumento.valueProperty())
                .registrar("numeroDocumento", txtNumeroDocumento, lblErrorNumeroDocumento,
                        txtNumeroDocumento.textProperty())
                .registrar("nombre", txtNombre, lblErrorNombre, txtNombre.textProperty())
                .registrar("apellido", txtApellido, lblErrorApellido, txtApellido.textProperty())
                .registrar("condicionIva", cmbCondicionIva, lblErrorCondicionIva,
                        cmbCondicionIva.valueProperty())
                .registrar("telefono", txtTelefono, lblErrorTelefono, txtTelefono.textProperty())
                .registrar("email", txtEmail, lblErrorEmail, txtEmail.textProperty())
                .registrar("direccion", txtDireccion, lblErrorDireccion, txtDireccion.textProperty());

        lblTitulo.setText("Nuevo cliente");
        actualizarSegunTipoDocumento(cmbTipoDocumento.getValue());
        txtNumeroDocumento.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnCancelar.setOnAction(e -> volver());

        cmbTipoDocumento.valueProperty().addListener((obs, o, tipo) -> {
            actualizarSegunTipoDocumento(tipo);
            // Un cambio de tipo puede resolver el error de apellido o de condición frente al IVA.
            errores.limpiar("apellido");
            errores.limpiar("condicionIva");
        });
    }

    @Override
    public void setData(ClienteDto cliente) {

        clienteActual = cliente;

        lblTitulo.setText("Editar cliente");
        cmbTipoDocumento.setValue(cliente.getTipoDocumento());
        txtNumeroDocumento.setText(cliente.getDocumentoFormateado());
        txtNombre.setText(cliente.getNombre());
        txtApellido.setText(cliente.getApellido());
        cmbCondicionIva.setValue(cliente.getCondicionIva());
        txtTelefono.setText(cliente.getTelefono());
        txtEmail.setText(cliente.getEmail());
        txtDireccion.setText(cliente.getDireccion());

        errores.limpiar();
    }

    /**
     * Con CUIT el cliente puede ser una empresa: el nombre pasa a ser la razón
     * social y el apellido es opcional. Para los demás documentos es una persona.
     */
    private void actualizarSegunTipoDocumento(TipoDocumento tipo) {

        boolean puedeSerEmpresa = tipo == TipoDocumento.CUIT;

        lblNombre.setText(puedeSerEmpresa ? "Nombre o razón social *" : "Nombre *");
        lblApellido.setText(puedeSerEmpresa ? "Apellido (vacío si es empresa)" : "Apellido *");

        txtNombre.setPromptText(puedeSerEmpresa ? "Ej.: Distribuidora del Centro S.A." : "Ej.: Juan");

        txtNumeroDocumento.setPromptText(tipo == null ? "" : switch (tipo) {
            case DNI -> "Ej.: 30123456";
            case CUIT -> "Ej.: 30-71234567-1";
            case CUIL -> "Ej.: 20-30123456-3";
            case PASAPORTE -> "Ej.: AAB123456";
        });
    }

    private void guardar() {

        errores.limpiar();

        execute(() -> {

            ClienteDto dto = construirDto();

            try {

                if (clienteActual == null) {
                    clienteService.save(dto);
                    success("Cliente creado correctamente.");
                } else {
                    clienteService.update(dto);
                    success("Cliente actualizado correctamente.");
                }

            } catch (ValidationException ex) {
                errores.mostrar(ex.getValidationResult());
                return;
            }

            volver();
        });
    }

    private ClienteDto construirDto() {

        return ClienteDto.builder()
                .id(clienteActual != null ? clienteActual.getId() : null)
                .tipoDocumento(cmbTipoDocumento.getValue())
                .numeroDocumento(txtNumeroDocumento.getText())
                .nombre(txtNombre.getText())
                .apellido(txtApellido.getText())
                .condicionIva(cmbCondicionIva.getValue())
                .telefono(txtTelefono.getText())
                .email(txtEmail.getText())
                .direccion(txtDireccion.getText())
                .build();
    }

    private void volver() {
        navigator.navigate(AppView.CLIENTE_LIST);
    }
}
