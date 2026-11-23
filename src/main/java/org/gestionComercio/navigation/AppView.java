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

    /**
     * Pantalla por defecto al iniciar sesión, disponible para cualquier usuario
     * con sesión iniciada. Cada indicador se muestra según su permiso.
     */
    INICIO(
            "/org/gestionComercio/view/inicio/Inicio.fxml",
            "Inicio",
            NavigationType.CONTENT,
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

    MARCA_LIST(
            "/org/gestionComercio/view/marca/MarcaList.fxml",
            "Marcas",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.MARCA_VER
    ),

    MARCA_FORM(
            "/org/gestionComercio/view/marca/MarcaForm.fxml",
            "Marca",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.MARCA_CREAR,
            PermisoCodigo.MARCA_EDITAR
    ),

    PROVEEDOR_LIST(
            "/org/gestionComercio/view/proveedor/ProveedorList.fxml",
            "Proveedores",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.PROVEEDOR_VER
    ),

    PROVEEDOR_FORM(
            "/org/gestionComercio/view/proveedor/ProveedorForm.fxml",
            "Proveedor",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.PROVEEDOR_CREAR,
            PermisoCodigo.PROVEEDOR_EDITAR
    ),

    CLIENTE_LIST(
            "/org/gestionComercio/view/cliente/ClienteList.fxml",
            "Clientes",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.CLIENTE_VER
    ),

    CLIENTE_FORM(
            "/org/gestionComercio/view/cliente/ClienteForm.fxml",
            "Cliente",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.CLIENTE_CREAR,
            PermisoCodigo.CLIENTE_EDITAR
    ),

    PRODUCTO_LIST(
            "/org/gestionComercio/view/producto/ProductoList.fxml",
            "Productos",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.PRODUCTO_VER
    ),

    PRODUCTO_FORM(
            "/org/gestionComercio/view/producto/ProductoForm.fxml",
            "Producto",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.PRODUCTO_CREAR,
            PermisoCodigo.PRODUCTO_EDITAR
    ),

    STOCK_CONSULTA(
            "/org/gestionComercio/view/inventario/StockConsulta.fxml",
            "Consulta de stock",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.INVENTARIO_VER
    ),

    /** Acepta como dato el ID de un producto, para abrir con su historial filtrado. */
    MOVIMIENTO_HISTORIAL(
            "/org/gestionComercio/view/inventario/MovimientoHistorial.fxml",
            "Historial de movimientos",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.INVENTARIO_VER
    ),

    /**
     * Caja del usuario de la sesión. Abrir, registrar movimientos y cerrar se
     * habilitan según CAJA_ABRIR, CAJA_REGISTRAR_MOVIMIENTO y CAJA_CERRAR.
     */
    CAJA(
            "/org/gestionComercio/view/caja/Caja.fxml",
            "Caja",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.CAJA_VER
    ),

    CAJA_HISTORIAL(
            "/org/gestionComercio/view/caja/CajaHistorial.fxml",
            "Historial de cajas",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.CAJA_VER
    ),

    /** Punto de venta: cargar el carrito y confirmar la venta en la caja abierta del vendedor. */
    PUNTO_VENTA(
            "/org/gestionComercio/view/venta/PuntoVenta.fxml",
            "Punto de venta",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.VENTA_CREAR
    ),

    VENTA_LIST(
            "/org/gestionComercio/view/venta/VentaList.fxml",
            "Ventas",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.VENTA_VER
    ),

    /** Recibir y anular se habilitan según COMPRA_CREAR y COMPRA_ANULAR. */
    COMPRA_LIST(
            "/org/gestionComercio/view/compra/CompraList.fxml",
            "Compras",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.COMPRA_VER
    ),

    COMPRA_FORM(
            "/org/gestionComercio/view/compra/CompraForm.fxml",
            "Nueva compra",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.COMPRA_CREAR
    ),

    /** Cada reporte se ofrece según su permiso REPORTE_* (ver TipoReporte). */
    REPORTES(
            "/org/gestionComercio/view/reporte/Reportes.fxml",
            "Reportes",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.REPORTE_VENTAS,
            PermisoCodigo.REPORTE_INVENTARIO,
            PermisoCodigo.REPORTE_COMPRAS,
            PermisoCodigo.REPORTE_CAJA
    ),

    /** Se ve con EMPRESA_VER; sin EMPRESA_EDITAR el formulario es de sólo lectura. */
    EMPRESA_CONFIG(
            "/org/gestionComercio/view/empresa/EmpresaConfig.fxml",
            "Configuración de la empresa",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.EMPRESA_VER
    ),

    /** Certificado de ARCA, ambiente, punto de venta y guía para configurar la facturación electrónica. */
    FACTURACION_CONFIG(
            "/org/gestionComercio/view/facturacion/FacturacionConfig.fxml",
            "Facturación electrónica",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.FACTURACION_CONFIGURAR
    ),

    /** Comprobantes electrónicos sin CAE (pendientes, sin respuesta o rechazados) y su reintento. */
    FACTURACION_PENDIENTES(
            "/org/gestionComercio/view/facturacion/ComprobantesPendientes.fxml",
            "Comprobantes pendientes de CAE",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.FACTURACION_REINTENTAR
    ),

    /** Estado de la licencia, código de esta PC y activación de una licencia nueva. */
    LICENCIA(
            "/org/gestionComercio/view/licencia/Licencia.fxml",
            "Licencia",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.LICENCIA_ADMINISTRAR
    ),

    /** Crear y restaurar se habilitan según BACKUP_CREAR y BACKUP_RESTAURAR. */
    BACKUP(
            "/org/gestionComercio/view/backup/Backup.fxml",
            "Copias de seguridad",
            NavigationType.CONTENT,
            WindowMode.MAXIMIZED,
            false,
            PermisoCodigo.BACKUP_CREAR,
            PermisoCodigo.BACKUP_RESTAURAR
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
