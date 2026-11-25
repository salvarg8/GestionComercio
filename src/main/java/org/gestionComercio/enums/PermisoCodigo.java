package org.gestionComercio.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PermisoCodigo {

    // Usuarios
    USUARIO_VER("Usuarios", "Ver usuarios"),
    USUARIO_CREAR("Usuarios", "Crear usuarios"),
    USUARIO_EDITAR("Usuarios", "Editar usuarios"),
    USUARIO_ELIMINAR("Usuarios", "Eliminar usuarios"),

    // Roles
    ROL_VER("Roles", "Ver roles"),
    ROL_CREAR("Roles", "Crear roles"),
    ROL_EDITAR("Roles", "Editar roles"),
    ROL_ELIMINAR("Roles", "Eliminar roles"),

    // Permisos
    PERMISO_VER("Permisos", "Ver permisos"),

    // Empresa
    EMPRESA_VER("Empresa", "Ver empresas"),
    EMPRESA_EDITAR("Empresa", "Editar empresas"),

    // Configuración del sistema
    CONFIGURACION_VER("Configuración", "Ver los parámetros del sistema y el formato de los comprobantes"),
    CONFIGURACION_EDITAR("Configuración",
            "Modificar los parámetros del sistema, el formato de los comprobantes y cargar datos iniciales"),

    // Clientes
    CLIENTE_VER("Clientes", "Ver clientes"),
    CLIENTE_CREAR("Clientes", "Crear clientes"),
    CLIENTE_EDITAR("Clientes", "Editar clientes"),
    CLIENTE_DAR_BAJA("Clientes", "Dar de baja clientes"),
    CLIENTE_HABILITAR("Clientes", "Volver a habilitar clientes dados de baja"),

    // Productos
    PRODUCTO_VER("Productos", "Ver productos"),
    PRODUCTO_CREAR("Productos", "Crear productos"),
    PRODUCTO_EDITAR("Productos", "Editar productos"),
    PRODUCTO_DAR_BAJA("Productos", "Dar de baja productos"),
    PRODUCTO_HABILITAR("Productos", "Volver a habilitar productos dados de baja"),
    PRODUCTO_ACTUALIZAR_PRECIOS("Productos",
            "Actualizar los precios de muchos productos a la vez por porcentaje y ver el historial"),

    // Inventario
    INVENTARIO_VER("Inventario", "Consultar stock y ver movimientos de stock"),
    INVENTARIO_REGISTRAR_ENTRADA("Inventario", "Registrar entradas de stock"),
    INVENTARIO_REGISTRAR_SALIDA("Inventario", "Registrar salidas de stock"),
    INVENTARIO_AJUSTAR("Inventario", "Registrar ajustes de stock"),

    // Categorías
    CATEGORIA_VER("Categorías", "Ver categorías"),
    CATEGORIA_CREAR("Categorías", "Crear categorías"),
    CATEGORIA_EDITAR("Categorías", "Editar categorías"),
    CATEGORIA_ELIMINAR("Categorías", "Eliminar categorías"),

    // Marcas
    MARCA_VER("Marcas", "Ver marcas"),
    MARCA_CREAR("Marcas", "Crear marcas"),
    MARCA_EDITAR("Marcas", "Editar marcas"),
    MARCA_DAR_BAJA("Marcas", "Dar de baja marcas"),
    MARCA_HABILITAR("Marcas", "Volver a habilitar marcas dadas de baja"),

    // Proveedores
    PROVEEDOR_VER("Proveedores", "Ver proveedores"),
    PROVEEDOR_CREAR("Proveedores", "Crear proveedores"),
    PROVEEDOR_EDITAR("Proveedores", "Editar proveedores"),
    PROVEEDOR_DAR_BAJA("Proveedores", "Dar de baja proveedores"),
    PROVEEDOR_HABILITAR("Proveedores", "Volver a habilitar proveedores dados de baja"),

    // Caja
    CAJA_VER("Caja", "Ver la caja propia, su historial y sus movimientos"),
    CAJA_VER_ABIERTAS_TODAS("Caja", "Ver las cajas abiertas de todos los usuarios y sus movimientos"),
    CAJA_VER_CERRADAS_TODAS("Caja", "Ver las cajas cerradas de todos los usuarios y sus movimientos"),
    CAJA_ABRIR("Caja", "Abrir caja"),
    CAJA_CERRAR("Caja", "Cerrar caja con arqueo"),
    CAJA_ABRIR_OTRO_USUARIO("Caja", "Abrir caja a nombre de otro usuario"),
    CAJA_CERRAR_OTRO_USUARIO("Caja", "Ver y cerrar con arqueo las cajas abiertas de otros usuarios"),
    CAJA_REGISTRAR_MOVIMIENTO("Caja", "Registrar ingresos, egresos, gastos y retiros de caja"),
    CAJA_CONFIRMAR_RETIRO("Caja",
            "Confirmar los retiros de caja cuando la empresa exige que los confirme un encargado"),

    // Ventas
    VENTA_VER("Ventas", "Ver ventas"),
    VENTA_CREAR("Ventas", "Crear ventas"),
    VENTA_ANULAR("Ventas", "Anular ventas"),

    // Facturación electrónica
    FACTURACION_CONFIGURAR("Facturación electrónica",
            "Configurar la facturación electrónica: certificado de ARCA, ambiente y punto de venta"),
    FACTURACION_REINTENTAR("Facturación electrónica",
            "Ver los comprobantes pendientes de CAE y reintentar su autorización en ARCA"),

    // Compras
    COMPRA_VER("Compras", "Ver compras"),
    COMPRA_CREAR("Compras", "Registrar y recibir compras"),
    COMPRA_ANULAR("Compras", "Anular compras"),

    // Reportes
    REPORTE_VENTAS("Reportes", "Reportes de ventas por período, forma de pago y usuario, y productos más vendidos"),
    REPORTE_INVENTARIO("Reportes", "Reportes de valorización del inventario y de productos con stock bajo o sin stock"),
    REPORTE_COMPRAS("Reportes", "Reporte de compras por proveedor"),
    REPORTE_CAJA("Reportes", "Reporte de movimientos de caja de todos los usuarios"),

    // Copias de seguridad
    BACKUP_CREAR("Copias de seguridad", "Crear copias de seguridad de la base de datos"),
    BACKUP_RESTAURAR("Copias de seguridad",
            "Restaurar la base de datos desde una copia de seguridad (reemplaza todos los datos)"),

    // Licencia
    LICENCIA_ADMINISTRAR("Licencia", "Ver el estado de la licencia del sistema y activar una licencia nueva");

    private final String modulo;
    private final String descripcion;
}