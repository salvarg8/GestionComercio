package org.gestionComercio.controller.proveedor;

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
import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ProveedorService;
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
public class ProveedorListController extends AbstractController {

    private static final PseudoClass DADO_DE_BAJA = PseudoClass.getPseudoClass("dado-de-baja");

    @FXML
    private ComboBox<FiltroEstado> cmbEstado;

    @FXML
    private TextField txtBuscar;

    @FXML
    private Button btnNuevo;

    @FXML
    private TableView<ProveedorDto> tblProveedores;

    @FXML
    private TableColumn<ProveedorDto, String> colRazonSocial;

    @FXML
    private TableColumn<ProveedorDto, String> colCuit;

    @FXML
    private TableColumn<ProveedorDto, String> colTelefono;

    @FXML
    private TableColumn<ProveedorDto, String> colEmail;

    @FXML
    private TableColumn<ProveedorDto, ProveedorDto> colEstado;

    @FXML
    private TableColumn<ProveedorDto, Void> colAcciones;

    @FXML
    private Label lblSinResultados;

    @FXML
    private Label lblTotal;

    private final ProveedorService proveedorService;
    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;

    @Override
    protected void initializeComponents() {

        boolean puedeCrear = sesionUsuario.tienePermiso(PermisoCodigo.PROVEEDOR_CREAR);
        btnNuevo.setVisible(puedeCrear);
        btnNuevo.setManaged(puedeCrear);

        configurarFiltroEstado();
        configurarColumnas();
        cargarProveedores();
        txtBuscar.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnNuevo.setOnAction(e -> nuevo());

        txtBuscar.textProperty().addListener((obs, oldValue, newValue) -> cargarProveedores());

        cmbEstado.valueProperty().addListener((obs, oldValue, newValue) -> cargarProveedores());
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
                    case ACTIVOS -> "Activos";
                    case DADOS_DE_BAJA -> "Dados de baja";
                    case TODOS -> "Todos";
                };
            }

            @Override
            public FiltroEstado fromString(String texto) {
                return null;
            }
        });
    }

    private void configurarColumnas() {

        colRazonSocial.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getRazonSocial()));

        colCuit.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getCuitFormateado()));

        colTelefono.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTelefono()));

        colEmail.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEmail()));

        colEstado.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue()));

        colEstado.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(ProveedorDto proveedor, boolean empty) {

                super.updateItem(proveedor, empty);

                if (empty || proveedor == null) {
                    setGraphic(null);
                    return;
                }

                badge.setText(proveedor.isActivo() ? "Activo" : "Dado de baja");
                badge.getStyleClass().setAll("label", "estado-badge",
                        proveedor.isActivo() ? "estado-activo" : "estado-inactivo");

                setGraphic(badge);
            }
        });

        // Las filas dadas de baja se ven atenuadas.
        tblProveedores.setRowFactory(tabla -> new TableRow<>() {

            @Override
            protected void updateItem(ProveedorDto proveedor, boolean empty) {
                super.updateItem(proveedor, empty);
                pseudoClassStateChanged(DADO_DE_BAJA,
                        !empty && proveedor != null && !proveedor.isActivo());
            }
        });

        configurarAcciones();
    }

    private void configurarAcciones() {

        List<Accion<ProveedorDto>> acciones = new ArrayList<>();

        if (sesionUsuario.tienePermiso(PermisoCodigo.PROVEEDOR_EDITAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.EDIT, "Editar",
                    "btn-icon-edit", ProveedorDto::isActivo, this::editar));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.PROVEEDOR_DAR_BAJA)) {
            acciones.add(new Accion<>(FontAwesomeSolid.BAN, "Dar de baja",
                    "btn-icon-delete", ProveedorDto::isActivo, this::darDeBaja));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.PROVEEDOR_HABILITAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.CHECK_CIRCLE, "Volver a habilitar",
                    "btn-icon-enable", proveedor -> !proveedor.isActivo(), this::habilitar));
        }

        colAcciones.setVisible(!acciones.isEmpty());
        colAcciones.setCellFactory(column -> new RowActionsTableCell<>(acciones));
    }

    /**
     * Carga la tabla respetando el texto de búsqueda y el estado elegidos, así
     * después de cada operación no se pierde el filtro aplicado.
     */
    private void cargarProveedores() {

        String filtro = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();
        FiltroEstado estado = cmbEstado.getValue();

        List<ProveedorDto> proveedores = proveedorService.buscar(filtro, estado);

        tblProveedores.getItems().setAll(proveedores);

        lblSinResultados.setText(textoSinResultados(filtro, estado));

        lblTotal.setText("Total: " + proveedores.size()
                + (proveedores.size() == 1 ? " registro" : " registros"));
    }

    private String textoSinResultados(String filtro, FiltroEstado estado) {

        if (!filtro.isEmpty()) {
            return "No se encontraron proveedores que coincidan con \"" + filtro + "\".";
        }

        return estado == FiltroEstado.DADOS_DE_BAJA
                ? "No hay proveedores dados de baja."
                : "No hay proveedores cargados.";
    }

    private void nuevo() {
        navigator.navigate(AppView.PROVEEDOR_FORM);
    }

    private void editar(ProveedorDto proveedor) {
        navigator.navigate(AppView.PROVEEDOR_FORM, proveedor);
    }

    private void darDeBaja(ProveedorDto proveedor) {

        boolean confirmado = Dialogs.confirmar(
                "Dar de baja proveedor",
                "¿Desea dar de baja el proveedor \"" + proveedor.getRazonSocial() + "\"?\n\n"
                        + "Ya no se podrá elegir en productos ni compras nuevas; los que ya "
                        + "lo usan lo conservan. Se puede volver a habilitar más adelante.",
                "Dar de baja",
                true);

        if (!confirmado) {
            return;
        }

        execute(() -> {

            proveedorService.darDeBaja(proveedor.getId());

            cargarProveedores();

            success("Proveedor dado de baja correctamente.");
        });
    }

    private void habilitar(ProveedorDto proveedor) {

        boolean confirmado = Dialogs.confirmar(
                "Habilitar proveedor",
                "¿Desea volver a habilitar el proveedor \"" + proveedor.getRazonSocial() + "\"?",
                "Habilitar",
                false);

        if (!confirmado) {
            return;
        }

        execute(() -> {

            proveedorService.habilitar(proveedor.getId());

            cargarProveedores();

            success("Proveedor habilitado correctamente.");
        });
    }
}
