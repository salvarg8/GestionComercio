package org.gestionComercio.controller.marca;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.marca.MarcaDto;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.MarcaService;
import org.gestionComercio.util.dialog.Dialogs;
import org.gestionComercio.view.component.table.RowActionsTableCell;
import org.gestionComercio.view.component.table.RowActionsTableCell.Accion;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class MarcaListController extends AbstractController {

    private static final PseudoClass DADO_DE_BAJA = PseudoClass.getPseudoClass("dado-de-baja");

    @FXML
    private ComboBox<FiltroEstado> cmbEstado;

    @FXML
    private TextField txtBuscar;

    @FXML
    private Button btnNuevo;

    @FXML
    private TableView<MarcaDto> tblMarcas;

    @FXML
    private TableColumn<MarcaDto, String> colNombre;

    @FXML
    private TableColumn<MarcaDto, String> colDescripcion;

    @FXML
    private TableColumn<MarcaDto, MarcaDto> colEstado;

    @FXML
    private TableColumn<MarcaDto, Void> colAcciones;

    @FXML
    private Label lblSinResultados;

    @FXML
    private Label lblTotal;

    private final MarcaService marcaService;
    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;

    @Override
    protected void initializeComponents() {

        boolean puedeCrear = sesionUsuario.tienePermiso(PermisoCodigo.MARCA_CREAR);
        btnNuevo.setVisible(puedeCrear);
        btnNuevo.setManaged(puedeCrear);

        configurarFiltroEstado();
        configurarColumnas();
        cargarMarcas();
        txtBuscar.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnNuevo.setOnAction(e -> nueva());

        txtBuscar.textProperty().addListener((obs, oldValue, newValue) -> cargarMarcas());

        cmbEstado.valueProperty().addListener((obs, oldValue, newValue) -> cargarMarcas());
    }

    private void configurarFiltroEstado() {

        cmbEstado.getItems().setAll(FiltroEstado.values());
        cmbEstado.setValue(FiltroEstado.ACTIVOS);

        cmbEstado.setConverter(new StringConverter<>() {

            @Override
            public String toString(FiltroEstado filtro) {
                if (filtro == null) {
                    return "";
                }
                return switch (filtro) {
                    case ACTIVOS -> "Activas";
                    case DADOS_DE_BAJA -> "Dadas de baja";
                    case TODOS -> "Todas";
                };
            }

            @Override
            public FiltroEstado fromString(String texto) {
                return null;
            }
        });
    }

    private void configurarColumnas() {

        colNombre.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNombre()));

        colDescripcion.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDescripcion()));

        colEstado.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue()));

        colEstado.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(MarcaDto marca, boolean empty) {

                super.updateItem(marca, empty);

                if (empty || marca == null) {
                    setGraphic(null);
                    return;
                }

                badge.setText(marca.isActiva() ? "Activa" : "Dada de baja");
                badge.getStyleClass().setAll("label", "estado-badge",
                        marca.isActiva() ? "estado-activo" : "estado-inactivo");

                setGraphic(badge);
            }
        });

        // Las filas dadas de baja se ven atenuadas.
        tblMarcas.setRowFactory(tabla -> new TableRow<>() {

            @Override
            protected void updateItem(MarcaDto marca, boolean empty) {
                super.updateItem(marca, empty);
                pseudoClassStateChanged(DADO_DE_BAJA, !empty && marca != null && !marca.isActiva());
            }
        });

        configurarAcciones();
    }

    private void configurarAcciones() {

        List<Accion<MarcaDto>> acciones = new ArrayList<>();

        if (sesionUsuario.tienePermiso(PermisoCodigo.MARCA_EDITAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.EDIT, "Editar",
                    "btn-icon-edit", MarcaDto::isActiva, this::editar));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.MARCA_DAR_BAJA)) {
            acciones.add(new Accion<>(FontAwesomeSolid.BAN, "Dar de baja",
                    "btn-icon-delete", MarcaDto::isActiva, this::darDeBaja));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.MARCA_HABILITAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.CHECK_CIRCLE, "Volver a habilitar",
                    "btn-icon-enable", marca -> !marca.isActiva(), this::habilitar));
        }

        colAcciones.setVisible(!acciones.isEmpty());
        colAcciones.setCellFactory(column -> new RowActionsTableCell<>(acciones));
    }

    /**
     * Carga la tabla respetando el texto de búsqueda y el estado elegidos, así
     * después de cada operación no se pierde el filtro aplicado.
     */
    private void cargarMarcas() {

        String filtro = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();
        FiltroEstado estado = cmbEstado.getValue();

        List<MarcaDto> marcas = marcaService.buscar(filtro, estado);

        tblMarcas.getItems().setAll(marcas);

        lblSinResultados.setText(textoSinResultados(filtro, estado));

        lblTotal.setText("Total: " + marcas.size()
                + (marcas.size() == 1 ? " registro" : " registros"));
    }

    private String textoSinResultados(String filtro, FiltroEstado estado) {

        if (!filtro.isEmpty()) {
            return "No se encontraron marcas que coincidan con \"" + filtro + "\".";
        }

        return estado == FiltroEstado.DADOS_DE_BAJA
                ? "No hay marcas dadas de baja."
                : "No hay marcas cargadas.";
    }

    private void nueva() {
        navigator.navigate(AppView.MARCA_FORM);
    }

    private void editar(MarcaDto marca) {
        navigator.navigate(AppView.MARCA_FORM, marca);
    }

    private void darDeBaja(MarcaDto marca) {

        boolean confirmado = Dialogs.confirmar(
                "Dar de baja marca",
                "¿Desea dar de baja la marca \"" + marca.getNombre() + "\"?\n\n"
                        + "Ya no se podrá elegir en productos nuevos; los productos que "
                        + "la usan la conservan. Se puede volver a habilitar más adelante.",
                "Dar de baja",
                true);

        if (!confirmado) {
            return;
        }

        execute(() -> {

            marcaService.darDeBaja(marca.getId());

            cargarMarcas();

            success("Marca dada de baja correctamente.");
        });
    }

    private void habilitar(MarcaDto marca) {

        boolean confirmado = Dialogs.confirmar(
                "Habilitar marca",
                "¿Desea volver a habilitar la marca \"" + marca.getNombre() + "\"?",
                "Habilitar",
                false);

        if (!confirmado) {
            return;
        }

        execute(() -> {

            marcaService.habilitar(marca.getId());

            cargarMarcas();

            success("Marca habilitada correctamente.");
        });
    }
}
