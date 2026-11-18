package org.gestionComercio.controller.cliente;

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
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ClienteService;
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
public class ClienteListController extends AbstractController {

    private static final PseudoClass DADO_DE_BAJA = PseudoClass.getPseudoClass("dado-de-baja");

    @FXML
    private ComboBox<FiltroEstado> cmbEstado;

    @FXML
    private TextField txtBuscar;

    @FXML
    private Button btnNuevo;

    @FXML
    private TableView<ClienteDto> tblClientes;

    @FXML
    private TableColumn<ClienteDto, String> colNombre;

    @FXML
    private TableColumn<ClienteDto, String> colDocumento;

    @FXML
    private TableColumn<ClienteDto, String> colCondicionIva;

    @FXML
    private TableColumn<ClienteDto, String> colTelefono;

    @FXML
    private TableColumn<ClienteDto, String> colEmail;

    @FXML
    private TableColumn<ClienteDto, ClienteDto> colEstado;

    @FXML
    private TableColumn<ClienteDto, Void> colAcciones;

    @FXML
    private Label lblSinResultados;

    @FXML
    private Label lblTotal;

    private final ClienteService clienteService;
    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;

    @Override
    protected void initializeComponents() {

        boolean puedeCrear = sesionUsuario.tienePermiso(PermisoCodigo.CLIENTE_CREAR);
        btnNuevo.setVisible(puedeCrear);
        btnNuevo.setManaged(puedeCrear);

        configurarFiltroEstado();
        configurarColumnas();
        cargarClientes();
        txtBuscar.requestFocus();
    }

    @Override
    protected void initializeEvents() {

        btnNuevo.setOnAction(e -> nuevo());

        txtBuscar.textProperty().addListener((obs, oldValue, newValue) -> cargarClientes());

        cmbEstado.valueProperty().addListener((obs, oldValue, newValue) -> cargarClientes());
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

        colNombre.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getNombreCompleto()));

        colDocumento.setCellValueFactory(c -> {
            ClienteDto cliente = c.getValue();
            String tipo = cliente.getTipoDocumento() == null ? "" : cliente.getTipoDocumento() + " ";
            return new SimpleStringProperty(tipo + cliente.getDocumentoFormateado());
        });

        colCondicionIva.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getCondicionIva() == null
                        ? ""
                        : c.getValue().getCondicionIva().getDescripcion()));

        colTelefono.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTelefono()));

        colEmail.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEmail()));

        colEstado.setCellValueFactory(c ->
                new SimpleObjectProperty<>(c.getValue()));

        colEstado.setCellFactory(column -> new TableCell<>() {

            private final Label badge = new Label();

            @Override
            protected void updateItem(ClienteDto cliente, boolean empty) {

                super.updateItem(cliente, empty);

                if (empty || cliente == null) {
                    setGraphic(null);
                    return;
                }

                badge.setText(cliente.isActivo() ? "Activo" : "Dado de baja");
                badge.getStyleClass().setAll("label", "estado-badge",
                        cliente.isActivo() ? "estado-activo" : "estado-inactivo");

                setGraphic(badge);
            }
        });

        // Las filas dadas de baja se ven atenuadas.
        tblClientes.setRowFactory(tabla -> new TableRow<>() {

            @Override
            protected void updateItem(ClienteDto cliente, boolean empty) {
                super.updateItem(cliente, empty);
                pseudoClassStateChanged(DADO_DE_BAJA,
                        !empty && cliente != null && !cliente.isActivo());
            }
        });

        configurarAcciones();
    }

    private void configurarAcciones() {

        List<Accion<ClienteDto>> acciones = new ArrayList<>();

        if (sesionUsuario.tienePermiso(PermisoCodigo.CLIENTE_EDITAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.EDIT, "Editar",
                    "btn-icon-edit", ClienteDto::isActivo, this::editar));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.CLIENTE_DAR_BAJA)) {
            acciones.add(new Accion<>(FontAwesomeSolid.BAN, "Dar de baja",
                    "btn-icon-delete", ClienteDto::isActivo, this::darDeBaja));
        }

        if (sesionUsuario.tienePermiso(PermisoCodigo.CLIENTE_HABILITAR)) {
            acciones.add(new Accion<>(FontAwesomeSolid.CHECK_CIRCLE, "Volver a habilitar",
                    "btn-icon-enable", cliente -> !cliente.isActivo(), this::habilitar));
        }

        colAcciones.setVisible(!acciones.isEmpty());
        colAcciones.setCellFactory(column -> new RowActionsTableCell<>(acciones));
    }

    /**
     * Carga la tabla respetando el texto de búsqueda y el estado elegidos, así
     * después de cada operación no se pierde el filtro aplicado.
     */
    private void cargarClientes() {

        String filtro = txtBuscar.getText() == null ? "" : txtBuscar.getText().trim();
        FiltroEstado estado = cmbEstado.getValue();

        List<ClienteDto> clientes = clienteService.buscar(filtro, estado);

        tblClientes.getItems().setAll(clientes);

        lblSinResultados.setText(textoSinResultados(filtro, estado));

        lblTotal.setText("Total: " + clientes.size()
                + (clientes.size() == 1 ? " registro" : " registros"));
    }

    private String textoSinResultados(String filtro, FiltroEstado estado) {

        if (!filtro.isEmpty()) {
            return "No se encontraron clientes que coincidan con \"" + filtro + "\".";
        }

        return estado == FiltroEstado.DADOS_DE_BAJA
                ? "No hay clientes dados de baja."
                : "No hay clientes cargados.";
    }

    private void nuevo() {
        navigator.navigate(AppView.CLIENTE_FORM);
    }

    private void editar(ClienteDto cliente) {
        navigator.navigate(AppView.CLIENTE_FORM, cliente);
    }

    private void darDeBaja(ClienteDto cliente) {

        boolean confirmado = Dialogs.confirmar(
                "Dar de baja cliente",
                "¿Desea dar de baja el cliente \"" + cliente.getNombreCompleto() + "\"?\n\n"
                        + "Ya no se podrá elegir en ventas nuevas; las ventas que ya tiene "
                        + "lo conservan. Se puede volver a habilitar más adelante.",
                "Dar de baja",
                true);

        if (!confirmado) {
            return;
        }

        execute(() -> {

            clienteService.darDeBaja(cliente.getId());

            cargarClientes();

            success("Cliente dado de baja correctamente.");
        });
    }

    private void habilitar(ClienteDto cliente) {

        boolean confirmado = Dialogs.confirmar(
                "Habilitar cliente",
                "¿Desea volver a habilitar el cliente \"" + cliente.getNombreCompleto() + "\"?",
                "Habilitar",
                false);

        if (!confirmado) {
            return;
        }

        execute(() -> {

            clienteService.habilitar(cliente.getId());

            cargarClientes();

            success("Cliente habilitado correctamente.");
        });
    }
}
