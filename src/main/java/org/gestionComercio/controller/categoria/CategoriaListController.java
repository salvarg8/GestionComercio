package org.gestionComercio.controller.categoria;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.categoria.CategoriaDto;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.CategoriaService;
import org.gestionComercio.view.component.table.ActionTableCell;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class CategoriaListController extends AbstractController {

    @FXML
    private TextField txtBuscar;

    @FXML
    private Button btnNuevo;

    @FXML
    private TableView<CategoriaDto> tblCategorias;

    @FXML
    private TableColumn<CategoriaDto, String> colNombre;

    @FXML
    private TableColumn<CategoriaDto, String> colDescripcion;

    @FXML
    private TableColumn<CategoriaDto, Void> colAcciones;

    @FXML
    private Label lblSinResultados;

    @FXML
    private Label lblTotal;

    private final CategoriaService categoriaService;
    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;

    @Override
    protected void initializeComponents() {

        boolean puedeCrear = sesionUsuario.tienePermiso(PermisoCodigo.CATEGORIA_CREAR);
        btnNuevo.setVisible(puedeCrear);
        btnNuevo.setManaged(puedeCrear);

        configurarColumnas();
        cargarCategorias();
        txtBuscar.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnNuevo.setOnAction(e -> nueva());

        txtBuscar.textProperty().addListener(
                (obs, oldValue, newValue) -> cargarCategorias());
    }

    private void configurarColumnas() {

        colNombre.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNombre()));

        colDescripcion.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDescripcion()));

        boolean puedeEditar = sesionUsuario.tienePermiso(PermisoCodigo.CATEGORIA_EDITAR);
        boolean puedeEliminar = sesionUsuario.tienePermiso(PermisoCodigo.CATEGORIA_ELIMINAR);

        colAcciones.setVisible(puedeEditar || puedeEliminar);

        colAcciones.setCellFactory(column ->
                new ActionTableCell<CategoriaDto>(
                        puedeEditar ? this::editar : null,
                        puedeEliminar ? this::eliminar : null
                ));
    }

    /**
     * Carga la tabla respetando el texto de búsqueda actual, así después de
     * eliminar no se pierde el filtro que el usuario tenía aplicado.
     */
    private void cargarCategorias() {

        String filtro = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();
        boolean filtrando = !filtro.isEmpty();

        List<CategoriaDto> categorias = filtrando
                ? categoriaService.search(filtro)
                : categoriaService.findAll();

        tblCategorias.getItems().setAll(categorias);

        lblSinResultados.setText(filtrando
                ? "No se encontraron categorías que coincidan con \"" + filtro + "\"."
                : "No hay categorías cargadas.");

        lblTotal.setText("Total: " + categorias.size()
                + (categorias.size() == 1 ? " registro" : " registros"));
    }

    private void nueva() {
        navigator.navigate(AppView.CATEGORIA_FORM);
    }

    private void editar(CategoriaDto categoria) {
        navigator.navigate(AppView.CATEGORIA_FORM, categoria);
    }

    private void eliminar(CategoriaDto categoria) {

        if (!confirmDelete(
                "Eliminar categoría",
                "¿Desea eliminar la categoría \"" + categoria.getNombre() + "\"?")) {
            return;
        }

        execute(() -> {

            categoriaService.delete(categoria.getId());

            cargarCategorias();

            success("Categoría eliminada correctamente.");
        });
    }
}
