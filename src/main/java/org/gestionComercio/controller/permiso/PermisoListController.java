package org.gestionComercio.controller.permiso;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.permiso.PermisoDto;
import org.gestionComercio.service.PermisoService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PermisoListController extends AbstractController {

    private final PermisoService permisoService;

    @FXML
    private TextField txtBuscar;

    @FXML
    private TableView<PermisoDto> tblPermisos;

    @FXML
    private TableColumn<PermisoDto, String> colCodigo;

    @FXML
    private TableColumn<PermisoDto, String> colDescripcion;

    @FXML
    private Label lblTotal;

    @Override
    protected void initializeComponents() {
        configurarColumnas();
        cargarPermisos();
    }

    @Override
    protected void initializeEvents() {
        txtBuscar.textProperty().addListener((obs, oldValue, newValue) -> buscar(newValue));
    }

    private void configurarColumnas() {
        colCodigo.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getCodigo().name()));

        colDescripcion.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getDescripcion()));
    }

    private void cargarPermisos() {
        List<PermisoDto> permisos = permisoService.findAll();

        tblPermisos.getItems().setAll(permisos);
        actualizarTotal(permisos.size());
    }

    private void buscar(String texto) {
        List<PermisoDto> permisos = permisoService.search(texto);

        tblPermisos.getItems().setAll(permisos);
        actualizarTotal(permisos.size());
    }

    private void actualizarTotal(int total) {
        lblTotal.setText("Total: " + total + " permiso" + (total == 1 ? "" : "s"));
    }
}