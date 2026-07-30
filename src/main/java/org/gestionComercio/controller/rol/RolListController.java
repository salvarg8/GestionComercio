package org.gestionComercio.controller.rol;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.rol.RolDto;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.RolService;
import org.gestionComercio.view.component.table.ActionTableCell;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class RolListController extends AbstractController {

    @FXML
    private TextField txtBuscar;

    @FXML
    private Button btnNuevo;

    @FXML
    private TableView<RolDto> tblRoles;

    @FXML
    private TableColumn<RolDto, String> colNombre;

    @FXML
    private TableColumn<RolDto, String> colDescripcion;

    @FXML
    private TableColumn<RolDto, Void> colAcciones;

    @FXML
    private Label lblTotal;

    private final RolService rolService;
    private final Navigator navigator;


    @Override
    protected void initializeComponents() {

        configurarColumnas();
        cargarRoles();
        txtBuscar.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnNuevo.setOnAction(e -> nuevo());

        txtBuscar.textProperty().addListener(
                (obs, oldValue, newValue) -> buscar(newValue)
        );
    }

    private void cargarRoles() {

        mostrarRoles(rolService.findAll());

    }

    private void buscar(String texto) {

        List<RolDto> roles = texto == null || texto.isBlank()
                ? rolService.findAll()
                : rolService.search(texto);

        mostrarRoles(roles);
    }

    private void actualizarTotal() {

        lblTotal.setText(
                "Total: " + tblRoles.getItems().size() + " registros"
        );
    }

    private void configurarColumnas() {

        colNombre.setCellValueFactory(
                new PropertyValueFactory<>("nombre"));

        colDescripcion.setCellValueFactory(
                new PropertyValueFactory<>("descripcion"));

        configurarColumnaAcciones();
    }

    private void nuevo() {
        navigator.navigate(AppView.ROL_FORM);
    }

    private void configurarColumnaAcciones() {

        colAcciones.setCellFactory(column ->
                new ActionTableCell<>(
                        this::editar,
                        this::eliminar
                ));
    }

    private void editar(RolDto rol) {
        navigator.navigate(AppView.ROL_FORM, rol);
    }

    private void eliminar(RolDto rol) {

        if (!confirm(
                "Eliminar rol",
                "¿Desea eliminar el rol \"" + rol.getNombre() + "\"?")) {
            return;
        }

        execute(() -> {

            rolService.delete(rol.getId());

            cargarRoles();

            info("Rol eliminado correctamente.");
        });
    }

    private void mostrarRoles(List<RolDto> roles) {

        tblRoles.getItems().setAll(roles);

        actualizarTotal();
    }
}
