package org.gestionComercio.controller.usuario;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.usuario.UsuarioDto;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.UsuarioService;
import org.gestionComercio.view.component.table.ActionTableCell;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class UsuarioListController extends AbstractController {

    @FXML
    private TextField txtBuscar;

    @FXML
    private Button btnNuevo;

    @FXML
    private TableView<UsuarioDto> tblUsuarios;

    @FXML
    private TableColumn<UsuarioDto, String> colUsuario;

    @FXML
    private TableColumn<UsuarioDto, String> colNombre;

    @FXML
    private TableColumn<UsuarioDto, String> colDocumento;

    @FXML
    private TableColumn<UsuarioDto, String> colRol;

    @FXML
    private TableColumn<UsuarioDto, String> colEstado;

    @FXML
    private TableColumn<UsuarioDto, Void> colAcciones;

    @FXML
    private Label lblTotal;

    private final UsuarioService usuarioService;
    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;

    @Override
    protected void initializeComponents() {

        boolean puedeCrear = sesionUsuario.tienePermiso(PermisoCodigo.USUARIO_CREAR);
        btnNuevo.setVisible(puedeCrear);
        btnNuevo.setManaged(puedeCrear);

        configurarColumnas();
        cargarUsuarios();
        txtBuscar.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnNuevo.setOnAction(e -> nuevo());

        txtBuscar.textProperty().addListener(
                (obs, oldValue, newValue) -> buscar(newValue));
    }

    private void configurarColumnas() {

        colUsuario.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getUsuario()));

        colNombre.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNombreCompleto()));

        colDocumento.setCellValueFactory(c ->
                new SimpleStringProperty(
                        c.getValue().getTipoDocumento() + " " + c.getValue().getDocumento()));

        colRol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getRolNombre()));

        colEstado.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEstadoUsuario().name()));

        boolean puedeEditar = sesionUsuario.tienePermiso(PermisoCodigo.USUARIO_EDITAR);
        boolean puedeEliminar = sesionUsuario.tienePermiso(PermisoCodigo.USUARIO_ELIMINAR);

        colAcciones.setVisible(puedeEditar || puedeEliminar);

        colAcciones.setCellFactory(column ->
                new ActionTableCell<UsuarioDto>(
                        puedeEditar ? this::editar : null,
                        puedeEliminar ? this::eliminar : null
                ));
    }

    private void cargarUsuarios() {
        mostrarUsuarios(usuarioService.findAll());
    }

    private void buscar(String texto) {
        mostrarUsuarios(usuarioService.search(texto));
    }

    private void mostrarUsuarios(List<UsuarioDto> usuarios) {

        tblUsuarios.getItems().setAll(usuarios);

        lblTotal.setText("Total: " + usuarios.size() + " registros");
    }

    private void nuevo() {
        navigator.navigate(AppView.USUARIO_FORM);
    }

    private void editar(UsuarioDto usuario) {
        navigator.navigate(AppView.USUARIO_FORM, usuario);
    }

    private void eliminar(UsuarioDto usuario) {

        if (!confirmDelete(
                "Eliminar usuario",
                "¿Desea eliminar el usuario \"" + usuario.getUsuario() + "\"?")) {
            return;
        }

        execute(() -> {

            usuarioService.delete(usuario.getId());

            cargarUsuarios();

            success("Usuario eliminado correctamente.");
        });
    }
}
