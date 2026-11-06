package org.gestionComercio.navigation;

import javafx.scene.layout.StackPane;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.NavigationType;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.ErrorHandler;
import org.gestionComercio.security.SesionUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NavigatorTest {

    @Mock
    private StageManager stageManager;

    @Mock
    private ViewLoader viewLoader;

    @Mock
    private ErrorHandler errorHandler;

    private SesionUsuario sesionUsuario;

    private Navigator navigator;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        navigator = new Navigator(stageManager, viewLoader, sesionUsuario, errorHandler);
    }

    private void loguearCon(PermisoCodigo... permisos) {
        sesionUsuario.iniciarSesion(new Usuario(), Set.of(permisos));
    }

    private LoadedView vista(AppView view) {
        return new LoadedView(view, null, null);
    }

    @Test
    @DisplayName("✓ sin sesión, una vista protegida redirige al login")
    void sinSesionRedirigeAlLogin() {
        LoadedView login = vista(AppView.LOGIN);
        when(viewLoader.load(AppView.LOGIN)).thenReturn(login);

        navigator.navigate(AppView.DASHBOARD);

        verify(viewLoader, never()).load(AppView.DASHBOARD);
        verify(stageManager).showWindow(login);
    }

    @Test
    @DisplayName("✓ el login es accesible sin sesión")
    void loginEsPublico() {
        LoadedView login = vista(AppView.LOGIN);
        when(viewLoader.load(AppView.LOGIN)).thenReturn(login);

        navigator.navigate(AppView.LOGIN);

        verify(stageManager).showWindow(login);
        verify(errorHandler, never()).handle(any());
    }

    @Test
    @DisplayName("✓ con sesión y sin permisos se puede abrir el dashboard")
    void dashboardSoloRequiereSesion() {
        loguearCon();
        LoadedView dashboard = vista(AppView.DASHBOARD);
        when(viewLoader.load(AppView.DASHBOARD)).thenReturn(dashboard);

        navigator.navigate(AppView.DASHBOARD);

        verify(stageManager).showWindow(dashboard);
    }

    @Test
    @DisplayName("✓ sin el permiso requerido no se carga la vista y se informa el error")
    void sinPermisoNoNavega() {
        loguearCon(PermisoCodigo.ROL_VER);

        navigator.navigate(AppView.PERMISO_LIST);

        verify(viewLoader, never()).load(AppView.PERMISO_LIST);
        verify(errorHandler).handle(any(AuthorizationException.class));
    }

    @Test
    @DisplayName("✓ con el permiso requerido se muestra la vista")
    void conPermisoNavega() {
        loguearCon(PermisoCodigo.ROL_VER);
        LoadedView rolList = vista(AppView.ROL_LIST);
        when(viewLoader.load(AppView.ROL_LIST)).thenReturn(rolList);
        StackPane contentPane = mock(StackPane.class);
        navigator.setContentPane(contentPane);

        navigator.navigate(AppView.ROL_LIST);

        verify(stageManager).showContent(rolList, contentPane);
        verify(errorHandler, never()).handle(any());
    }

    @Test
    @DisplayName("✓ el formulario de roles admite crear o editar")
    void formularioRolAceptaCualquieraDeSusPermisos() {
        loguearCon(PermisoCodigo.ROL_EDITAR);
        assertTrue(navigator.puedeAcceder(AppView.ROL_FORM));

        loguearCon(PermisoCodigo.ROL_CREAR);
        assertTrue(navigator.puedeAcceder(AppView.ROL_FORM));

        loguearCon(PermisoCodigo.ROL_VER);
        assertFalse(navigator.puedeAcceder(AppView.ROL_FORM));
    }

    @Test
    @DisplayName("✓ después de cerrar sesión no se puede acceder a vistas protegidas")
    void cerrarSesionQuitaAcceso() {
        loguearCon(PermisoCodigo.ROL_VER);
        sesionUsuario.cerrarSesion();

        assertFalse(navigator.puedeAcceder(AppView.ROL_LIST));
        assertFalse(navigator.puedeAcceder(AppView.DASHBOARD));
        assertTrue(navigator.puedeAcceder(AppView.LOGIN));
    }

    @Test
    @DisplayName("✓ todas las vistas de contenido declaran un permiso (salvo las de uso personal)")
    void vistasDeContenidoDeclaranPermiso() {
        // Vistas que cualquier usuario autenticado puede abrir a propósito.
        Set<AppView> soloRequierenSesion = Set.of(AppView.CAMBIAR_PASSWORD);

        for (AppView view : AppView.values()) {
            if (view.getNavigationType() == NavigationType.CONTENT
                    && !soloRequierenSesion.contains(view)) {
                assertFalse(view.getPermisosRequeridos().isEmpty(),
                        view + " no declara permisos requeridos");
            }
        }
    }
}
