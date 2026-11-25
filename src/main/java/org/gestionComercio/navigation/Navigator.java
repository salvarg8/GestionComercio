package org.gestionComercio.navigation;

import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.ErrorHandler;
import org.gestionComercio.security.SesionUsuario;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class Navigator {

    private final StageManager stageManager;
    private final ViewLoader viewLoader;
    private final SesionUsuario sesionUsuario;
    private final ErrorHandler errorHandler;

    private StackPane contentPane;

    public void setContentPane(StackPane contentPane) {
        this.contentPane = contentPane;
    }

    public void navigate(AppView view) {
        navigate(view, null);
    }

    public void navigate(AppView view, Object data) {

        if (!view.isPublica() && !sesionUsuario.estaAutenticado()) {
            show(viewLoader.load(AppView.LOGIN));
            return;
        }

        if (!puedeAcceder(view)) {
            errorHandler.handle(new AuthorizationException(
                    "No tiene permiso para acceder a \"" + view.getTitulo() + "\"."));
            return;
        }

        // Las vistas de administración se muestran dentro de Configuración, en su pestaña.
        if (PestanaConfiguracion.de(view).isPresent()) {
            data = view;
            view = AppView.CONFIGURACION;
        }

        LoadedView loadedView = viewLoader.load(view);

        if (data != null && loadedView.controller() instanceof DataReceiver receiver) {
            receiver.setData(data);
        }

        show(loadedView);
    }

    /**
     * Abre la vista en una ventana modal, sin reemplazar la pantalla actual
     * (por ejemplo, para dar de alta un producto sin perder la compra que se
     * está cargando). Espera a que se cierre la ventana.
     *
     * @param configurar recibe el controlador de la vista antes de mostrarla
     */
    @SuppressWarnings("unchecked")
    public <C> void abrirModal(AppView view, Consumer<C> configurar) {

        if (!puedeAcceder(view)) {
            errorHandler.handle(new AuthorizationException(
                    "No tiene permiso para acceder a \"" + view.getTitulo() + "\"."));
            return;
        }

        LoadedView loadedView = viewLoader.load(view);

        configurar.accept((C) loadedView.controller());

        stageManager.showModal(loadedView);
    }

    /**
     * Indica si el usuario de la sesión actual puede acceder a la vista.
     */
    public boolean puedeAcceder(AppView view) {
        return view.isPublica()
                || (sesionUsuario.estaAutenticado()
                && sesionUsuario.tieneAlgunPermiso(view.getPermisosRequeridos()));
    }

    private void show(LoadedView loadedView) {

        switch (loadedView.navigationType()) {

            case WINDOW -> stageManager.showWindow(loadedView);

            case CONTENT -> {

                if (contentPane == null) {
                    throw new IllegalStateException(
                            "El contenedor principal del Dashboard no fue inicializado.");
                }

                stageManager.showContent(loadedView, contentPane);
            }

            case MODAL -> stageManager.showModal(loadedView);
        }
    }
}
