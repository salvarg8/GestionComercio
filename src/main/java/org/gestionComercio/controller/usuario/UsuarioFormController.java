package org.gestionComercio.controller.usuario;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.rol.RolDto;
import org.gestionComercio.dto.usuario.UsuarioDto;
import org.gestionComercio.enums.EstadoUsuario;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.UsuarioService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.Objects;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class UsuarioFormController extends AbstractController implements DataReceiver<UsuarioDto> {

    private final UsuarioService usuarioService;
    private final Navigator navigator;

    @FXML
    private Label lblTitulo;

    @FXML
    private TextField txtUsuario;

    @FXML
    private TextField txtNombre;

    @FXML
    private TextField txtApellido;

    @FXML
    private ComboBox<TipoDocumento> cmbTipoDocumento;

    @FXML
    private TextField txtDocumento;

    @FXML
    private ComboBox<RolDto> cmbRol;

    @FXML
    private ComboBox<EstadoUsuario> cmbEstado;

    @FXML
    private Label lblPassword;

    @FXML
    private PasswordField txtPassword;

    @FXML
    private PasswordField txtPasswordConfirmacion;

    @FXML
    private Label lblPasswordAyuda;

    @FXML
    private Button btnGuardar;

    @FXML
    private Button btnCancelar;

    /** null = alta; distinto de null = edición. */
    private UsuarioDto usuarioActual;

    @Override
    protected void initializeComponents() {

        cmbTipoDocumento.getItems().setAll(TipoDocumento.values());
        cmbTipoDocumento.setValue(TipoDocumento.DNI);

        cmbEstado.getItems().setAll(EstadoUsuario.values());
        cmbEstado.setValue(EstadoUsuario.ACTIVO);

        configurarComboRoles();

        modoAlta();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnCancelar.setOnAction(e -> volver());
    }

    @Override
    public void setData(UsuarioDto usuario) {

        usuarioActual = usuario;

        lblTitulo.setText("Editar usuario");

        txtUsuario.setText(usuario.getUsuario());
        txtNombre.setText(usuario.getNombre());
        txtApellido.setText(usuario.getApellido());
        cmbTipoDocumento.setValue(usuario.getTipoDocumento());
        txtDocumento.setText(usuario.getDocumento());
        cmbEstado.setValue(usuario.getEstadoUsuario());

        cmbRol.getItems().stream()
                .filter(rol -> Objects.equals(rol.getId(), usuario.getRolId()))
                .findFirst()
                .ifPresent(cmbRol::setValue);

        lblPassword.setText("Nueva contraseña");
        lblPasswordAyuda.setText("Dejar en blanco para mantener la contraseña actual.");
    }

    private void modoAlta() {

        lblTitulo.setText("Nuevo usuario");
        lblPassword.setText("Contraseña *");
        lblPasswordAyuda.setText("Mínimo 6 caracteres.");
    }

    private void configurarComboRoles() {

        cmbRol.setConverter(new StringConverter<>() {

            @Override
            public String toString(RolDto rol) {
                return rol == null ? "" : rol.getNombre();
            }

            @Override
            public RolDto fromString(String string) {
                return null;
            }
        });

        cmbRol.getItems().setAll(usuarioService.findRolesDisponibles());
    }

    private void guardar() {

        if (!Objects.equals(txtPassword.getText(), txtPasswordConfirmacion.getText())) {
            warning("La contraseña y su confirmación no coinciden.");
            return;
        }

        execute(() -> {

            UsuarioDto dto = construirDto();

            if (usuarioActual == null) {
                usuarioService.save(dto);
                info("Usuario creado correctamente.");
            } else {
                usuarioService.update(dto);
                info("Usuario actualizado correctamente.");
            }

            volver();
        });
    }

    private UsuarioDto construirDto() {

        RolDto rol = cmbRol.getValue();

        return UsuarioDto.builder()
                .id(usuarioActual != null ? usuarioActual.getId() : null)
                .usuario(txtUsuario.getText())
                .nombre(txtNombre.getText())
                .apellido(txtApellido.getText())
                .tipoDocumento(cmbTipoDocumento.getValue())
                .documento(txtDocumento.getText())
                .estadoUsuario(cmbEstado.getValue())
                .rolId(rol != null ? rol.getId() : null)
                .password(txtPassword.getText())
                .build();
    }

    private void volver() {
        navigator.navigate(AppView.USUARIO_LIST);
    }
}
