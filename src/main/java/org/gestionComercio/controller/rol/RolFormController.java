package org.gestionComercio.controller.rol;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.rol.RolDto;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.PermisoService;
import org.gestionComercio.service.RolService;
import org.gestionComercio.viewModel.PermisoItem;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class RolFormController extends AbstractController implements DataReceiver<RolDto> {
    private final RolService rolService;
    private final PermisoService permisoService;
    private final Navigator navigator;


    @FXML
    private TextField txtNombre;

    @FXML
    private TextArea txtDescripcion;

    @FXML
    private ListView<PermisoItem> lstPermisos;

    @FXML
    private Button btnGuardar;

    @FXML
    private Button btnCancelar;

    private RolDto rolActual;

    @Override
    protected void initializeComponents() {
        configurarListaPermisos();
        cargarPermisos();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(event -> guardar());

        btnCancelar.setOnAction(event -> cancelar());
    }

    private void configurarListaPermisos() {

        lstPermisos.setCellFactory(
                CheckBoxListCell.forListView(
                        PermisoItem::getSeleccionado,
                        new StringConverter<>() {

                            @Override
                            public String toString(PermisoItem item) {
                                return item.getPermiso().getDescripcion();
                            }

                            @Override
                            public PermisoItem fromString(String string) {
                                return null;
                            }
                        }
                )
        );
    }

    private void cargarPermisos() {

        List<PermisoItem> items = permisoService.findAll()
                .stream()
                .map(PermisoItem::new)
                .toList();

        lstPermisos.getItems().setAll(items);

    }

    private void limpiarFormulario() {

        rolActual = null;

        txtNombre.clear();
        txtDescripcion.clear();

        lstPermisos.getItems()
                .forEach(item -> item.getSeleccionado().set(false));
    }

    private void guardar() {

        execute(() -> {

            RolDto dto = construirDto();

            if (rolActual == null) {
                rolService.save(dto);
                info("Rol creado correctamente.");
            } else {
                rolService.update(dto);
                info("Rol actualizado correctamente.");
            }

            cancelar();
        });
    }

    private void cancelar() {
        navigator.navigate(AppView.ROL_LIST);
    }

    private RolDto construirDto() {

        return RolDto.builder()
                .id(rolActual != null ? rolActual.getId() : null)
                .nombre(txtNombre.getText())
                .descripcion(txtDescripcion.getText())
                .permisoIds(obtenerPermisosSeleccionados())
                .build();
    }

    private Set<Long> obtenerPermisosSeleccionados() {

        return lstPermisos.getItems()
                .stream()
                .filter(item -> item.getSeleccionado().get())
                .map(item -> item.getPermiso().getId())
                .collect(Collectors.toSet());
    }

    private void cargarRol(RolDto rol) {

        rolActual = rol;

        txtNombre.setText(rol.getNombre());
        txtDescripcion.setText(rol.getDescripcion());

        seleccionarPermisos(rol.getPermisoIds());
    }

    private void seleccionarPermisos(Set<Long> permisoIds) {

        if (permisoIds == null) {
            return;
        }

        lstPermisos.getItems().forEach(item ->
                item.getSeleccionado().set(
                        permisoIds.contains(item.getPermiso().getId())
                )
        );
    }

    @Override
    public void setData(RolDto rol) {
        cargarRol(rol);
    }
}