package org.gestionComercio.navigation;

import lombok.Getter;
import org.gestionComercio.enums.NavigationType;
import org.gestionComercio.enums.PermisoCodigo;

import java.util.Set;

@Getter
public enum AppView {

    LOGIN(
            "/org/gestionComercio/view/login/LoginView.fxml",
            "Iniciar sesión",
            NavigationType.WINDOW,
            WindowMode.AUTO_SIZE,
            true
    ),

    DASHBOARD(
            "/org/gestionComercio/view/dashboard/DashboardView.fxml",
            "Gestión Comercial",
            NavigationType.WINDOW,
            WindowMode.MAXIMIZED,
            false
    ),

    ROL_LIST(
            "/org/gestionComercio/view/rol/RolList.fxml",
            "Lista de roles",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.ROL_VER
    ),

    ROL_FORM(
            "/org/gestionComercio/view/rol/RolForm.fxml",
            "Roles",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.ROL_CREAR,
            PermisoCodigo.ROL_EDITAR
    ),

    PERMISO_LIST(
            "/org/gestionComercio/view/permiso/PermisoList.fxml",
            "Permisos",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.PERMISO_VER
    ),

    USUARIO_LIST(
            "/org/gestionComercio/view/usuario/UsuarioList.fxml",
            "Usuarios",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.USUARIO_VER
    ),

    USUARIO_FORM(
            "/org/gestionComercio/view/usuario/UsuarioForm.fxml",
            "Usuario",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.USUARIO_CREAR,
            PermisoCodigo.USUARIO_EDITAR
    ),

    CATEGORIA_LIST(
            "/org/gestionComercio/view/categoria/CategoriaList.fxml",
            "Categorías",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.CATEGORIA_VER
    ),

    CATEGORIA_FORM(
            "/org/gestionComercio/view/categoria/CategoriaForm.fxml",
            "Categoría",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.CATEGORIA_CREAR,
            PermisoCodigo.CATEGORIA_EDITAR
    ),

    /** Disponible para cualquier usuario con sesión iniciada. */
    CAMBIAR_PASSWORD(
            "/org/gestionComercio/view/perfil/CambiarPassword.fxml",
            "Cambiar contraseña",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false
    );

    private final String fxml;

    private final String titulo;

    private final NavigationType navigationType;

    private final WindowMode windowMode;

    /**
     * Indica si la vista puede mostrarse sin una sesión iniciada.
     */
    private final boolean publica;

    /**
     * Permisos que habilitan el acceso a la vista: alcanza con tener
     * cualquiera de ellos. Vacío significa que basta con estar autenticado.
     */
    private final Set<PermisoCodigo> permisosRequeridos;

    AppView(String fxml,
            String titulo,
            NavigationType navigationType,
            WindowMode windowMode,
            boolean publica,
            PermisoCodigo... permisosRequeridos) {

        this.fxml = fxml;
        this.titulo = titulo;
        this.navigationType = navigationType;
        this.windowMode = windowMode;
        this.publica = publica;
        this.permisosRequeridos = Set.of(permisosRequeridos);
    }
}
