package org.gestionComercio.controller.configuracion;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.exception.ErrorHandler;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.DataReceiver;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.navigation.PestanaConfiguracion;
import org.gestionComercio.navigation.ViewLoader;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuración: agrupa en pestañas las pantallas de administración (ver
 * {@link PestanaConfiguracion}). Sólo se muestran las pestañas a las que el
 * usuario puede acceder, y cada vista se carga recién la primera vez que se
 * abre su pestaña (por ejemplo, no se consulta ARCA si no se entra a
 * Facturación electrónica).
 *
 * <p>Recibe como dato la vista que se quiere mostrar y abre su pestaña.
 */
@Component
@RequiredArgsConstructor
@Scope("prototype")
public class ConfiguracionController extends AbstractController implements DataReceiver<AppView> {

    private final Navigator navigator;
    private final ViewLoader viewLoader;
    private final ErrorHandler errorHandler;

    @FXML
    private TabPane tabs;

    @Override
    protected void initializeComponents() {

        for (PestanaConfiguracion pestana : PestanaConfiguracion.values()) {

            List<AppView> vistas = pestana.getVistas().stream()
                    .filter(navigator::puedeAcceder)
                    .toList();

            if (vistas.isEmpty()) {
                continue;
            }

            Tab tab = new Tab(pestana.getTitulo());
            tab.setUserData(pestana);

            if (vistas.size() == 1) {
                tab.setContent(pendiente(vistas.get(0)));
            } else {
                tab.setContent(subPestanas(vistas));
            }

            tabs.getTabs().add(tab);
        }

        tabs.getSelectionModel().selectedItemProperty().addListener((obs, anterior, tab) -> cargar(tab));

        // Si no llega una vista en setData, se carga la primera pestaña.
        Platform.runLater(() -> cargar(tabs.getSelectionModel().getSelectedItem()));
    }

    /** Abre la pestaña (y sub-pestaña) de la vista. */
    @Override
    public void setData(AppView vista) {

        PestanaConfiguracion.de(vista).ifPresent(pestana -> {

            for (Tab tab : tabs.getTabs()) {

                if (tab.getUserData() != pestana) {
                    continue;
                }

                if (tab.getContent() instanceof TabPane internas) {
                    internas.getTabs().stream()
                            .filter(interna -> interna.getUserData() == vista)
                            .findFirst()
                            .ifPresent(internas.getSelectionModel()::select);
                }

                tabs.getSelectionModel().select(tab);
                cargar(tab);
            }
        });
    }

    private TabPane subPestanas(List<AppView> vistas) {

        TabPane internas = new TabPane();
        internas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        for (AppView vista : vistas) {
            Tab interna = new Tab(vista.getTitulo(), pendiente(vista));
            interna.setUserData(vista);
            internas.getTabs().add(interna);
        }

        internas.getSelectionModel().selectedItemProperty().addListener((obs, anterior, interna) -> {
            if (tabs.getSelectionModel().getSelectedItem() != null
                    && tabs.getSelectionModel().getSelectedItem().getContent() == internas) {
                cargar(interna);
            }
        });

        return internas;
    }

    /**
     * Carga la vista de la pestaña si todavía no se cargó. Una pestaña con
     * sub-pestañas carga la sub-pestaña seleccionada.
     */
    private void cargar(Tab tab) {

        if (tab == null) {
            return;
        }

        if (tab.getContent() instanceof TabPane internas) {
            cargar(internas.getSelectionModel().getSelectedItem());
            return;
        }

        if (!(tab.getContent() instanceof Pendiente pendiente)) {
            return;
        }

        try {
            tab.setContent(viewLoader.load(pendiente.vista).root());
        } catch (RuntimeException e) {
            Label error = new Label("No se pudo abrir \"" + pendiente.vista.getTitulo() + "\".");
            error.getStyleClass().add("form-error");
            tab.setContent(error);
            errorHandler.handle(e);
        }
    }

    private static Pendiente pendiente(AppView vista) {
        return new Pendiente(vista);
    }

    /** Contenido provisorio de una pestaña que todavía no se abrió. */
    private static final class Pendiente extends Label {

        private final AppView vista;

        private Pendiente(AppView vista) {
            this.vista = vista;
        }
    }
}
