package org.gestionComercio.controller.rol;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.rol.RolDto;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.PermisoService;
import org.gestionComercio.service.RolService;
import org.gestionComercio.viewModel.PermisoItem;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Alta y edición de roles. Los permisos se muestran agrupados por módulo: la
 * casilla del módulo tilda o destilda todos sus permisos (o los que coinciden
 * con la búsqueda). Sin permiso para guardar (por ejemplo, con sólo ROL_VER)
 * el rol se muestra en modo lectura.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class RolFormController extends AbstractController implements DataReceiver<RolDto> {

    private final RolService rolService;
    private final PermisoService permisoService;
    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;

    @FXML
    private Label lblTitulo;

    @FXML
    private Label lblSoloLectura;

    @FXML
    private TextField txtNombre;

    @FXML
    private TextArea txtDescripcion;

    @FXML
    private Label lblResumen;

    @FXML
    private TextField txtBuscarPermiso;

    @FXML
    private Label lblAyudaModulos;

    @FXML
    private TreeView<NodoPermiso> treePermisos;

    @FXML
    private Button btnGuardar;

    @FXML
    private Button btnCancelar;

    private RolDto rolActual;

    /** Todos los permisos; su selección se conserva aunque la búsqueda los oculte. */
    private List<PermisoItem> permisos = List.of();

    /** Enlaces entre las casillas del árbol y los permisos, para soltarlos al rearmarlo. */
    private final List<Runnable> desenlazar = new ArrayList<>();

    private boolean soloLectura;

    /** Fila del árbol: un módulo (permiso null) o uno de sus permisos. */
    private record NodoPermiso(String modulo, PermisoItem permiso) {
    }

    @Override
    protected void initializeComponents() {

        treePermisos.setRoot(new TreeItem<>());
        treePermisos.setCellFactory(tree -> new CeldaPermiso());

        cargarPermisos();
        aplicarModo();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(event -> guardar());

        btnCancelar.setOnAction(event -> cancelar());

        txtBuscarPermiso.textProperty().addListener((obs, anterior, texto) -> armarArbol());
    }

    @Override
    public void setData(RolDto rol) {
        cargarRol(rol);
        aplicarModo();
    }

    // ------------------------------------------------------------------
    // Permisos
    // ------------------------------------------------------------------

    private void cargarPermisos() {

        permisos = permisoService.findAll()
                .stream()
                .map(PermisoItem::new)
                .toList();

        permisos.forEach(item -> item.getSeleccionado().addListener((obs, o, n) -> actualizarResumen()));

        armarArbol();
        actualizarResumen();
    }

    /**
     * Arma el árbol con los módulos y permisos que coinciden con la búsqueda.
     * Con una búsqueda, los módulos se muestran abiertos.
     */
    private void armarArbol() {

        desenlazar.forEach(Runnable::run);
        desenlazar.clear();

        Map<String, List<PermisoItem>> grupos = PermisosPorModulo.agrupar(permisos, txtBuscarPermiso.getText());
        boolean buscando = txtBuscarPermiso.getText() != null && !txtBuscarPermiso.getText().isBlank();

        List<TreeItem<NodoPermiso>> modulos = new ArrayList<>();

        grupos.forEach((modulo, items) -> {

            CheckBoxTreeItem<NodoPermiso> nodoModulo = new CheckBoxTreeItem<>(new NodoPermiso(modulo, null));
            nodoModulo.setExpanded(buscando);

            for (PermisoItem item : items) {

                CheckBoxTreeItem<NodoPermiso> hoja = new CheckBoxTreeItem<>(new NodoPermiso(modulo, item));
                nodoModulo.getChildren().add(hoja);

                // Se enlaza después de agregarla: así el módulo se entera de su estado inicial.
                hoja.selectedProperty().bindBidirectional(item.getSeleccionado());
                desenlazar.add(() -> hoja.selectedProperty().unbindBidirectional(item.getSeleccionado()));
            }

            modulos.add(nodoModulo);
        });

        treePermisos.getRoot().getChildren().setAll(modulos);
    }

    private void actualizarResumen() {

        long seleccionados = permisos.stream().filter(item -> item.getSeleccionado().get()).count();

        lblResumen.setText(seleccionados + " de " + permisos.size() + " permisos seleccionados");
    }

    /** Casilla y texto; la casilla queda deshabilitada en modo lectura. */
    private final class CeldaPermiso extends TreeCell<NodoPermiso> {

        private final CheckBox check = new CheckBox();

        private CheckBoxTreeItem<NodoPermiso> enlazado;

        @Override
        protected void updateItem(NodoPermiso nodo, boolean empty) {

            super.updateItem(nodo, empty);

            if (enlazado != null) {
                check.selectedProperty().unbindBidirectional(enlazado.selectedProperty());
                check.indeterminateProperty().unbindBidirectional(enlazado.indeterminateProperty());
                enlazado = null;
            }

            if (empty || nodo == null || !(getTreeItem() instanceof CheckBoxTreeItem<NodoPermiso> item)) {
                setText(null);
                setGraphic(null);
                setStyle(null);
                return;
            }

            enlazado = item;
            check.selectedProperty().bindBidirectional(item.selectedProperty());
            check.indeterminateProperty().bindBidirectional(item.indeterminateProperty());
            check.setDisable(soloLectura);

            boolean esModulo = nodo.permiso() == null;
            setText(esModulo ? nodo.modulo() : nodo.permiso().getPermiso().getDescripcion());
            setStyle(esModulo ? "-fx-font-weight: bold;" : null);
            setGraphic(check);
        }
    }

    // ------------------------------------------------------------------
    // Rol
    // ------------------------------------------------------------------

    /**
     * Para guardar hace falta ROL_CREAR (rol nuevo) o ROL_EDITAR (rol
     * existente); sin ese permiso el formulario es de sólo lectura.
     */
    private void aplicarModo() {

        soloLectura = !sesionUsuario.tienePermiso(
                rolActual == null ? PermisoCodigo.ROL_CREAR : PermisoCodigo.ROL_EDITAR);

        lblTitulo.setText(soloLectura ? "Rol" : rolActual == null ? "Nuevo rol" : "Editar rol");

        lblSoloLectura.setVisible(soloLectura);
        lblSoloLectura.setManaged(soloLectura);
        lblAyudaModulos.setVisible(!soloLectura);
        lblAyudaModulos.setManaged(!soloLectura);

        txtNombre.setEditable(!soloLectura);
        txtDescripcion.setEditable(!soloLectura);

        btnGuardar.setVisible(!soloLectura);
        btnGuardar.setManaged(!soloLectura);
        btnCancelar.setText(soloLectura ? "Volver" : "Cancelar");

        treePermisos.refresh();
    }

    private void guardar() {

        execute(() -> {

            sesionUsuario.requerirPermiso(
                    rolActual == null ? PermisoCodigo.ROL_CREAR : PermisoCodigo.ROL_EDITAR);

            RolDto dto = construirDto();

            if (rolActual == null) {
                rolService.save(dto);
                success("Rol creado correctamente.");
            } else {
                rolService.update(dto);
                success("Rol actualizado correctamente.");
            }

            cancelar();
        });
    }

    /** Vuelve a Configuración, en la lista de roles. */
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

        return permisos.stream()
                .filter(item -> item.getSeleccionado().get())
                .map(item -> item.getPermiso().getId())
                .collect(Collectors.toSet());
    }

    private void cargarRol(RolDto rol) {

        rolActual = rol;

        txtNombre.setText(rol.getNombre());
        txtDescripcion.setText(rol.getDescripcion());

        Set<Long> permisoIds = rol.getPermisoIds() == null ? Set.of() : rol.getPermisoIds();

        permisos.forEach(item -> item.getSeleccionado().set(permisoIds.contains(item.getPermiso().getId())));
    }
}
