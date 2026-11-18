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

    // Clientes
    CLIENTE_VER("Clientes", "Ver clientes"),
    CLIENTE_CREAR("Clientes", "Crear clientes"),
    CLIENTE_EDITAR("Clientes", "Editar clientes"),
    CLIENTE_ELIMINAR("Clientes", "Eliminar clientes"),

    // Productos
    PRODUCTO_VER("Productos", "Ver productos"),
    PRODUCTO_CREAR("Productos", "Crear productos"),
    PRODUCTO_EDITAR("Productos", "Editar productos"),
    PRODUCTO_DAR_BAJA("Productos", "Dar de baja productos"),
    PRODUCTO_HABILITAR("Productos", "Volver a habilitar productos dados de baja"),

    // Inventario
    INVENTARIO_VER("Inventario", "Ver movimientos de stock"),
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

    // Ventas
    VENTA_VER("Ventas", "Ver ventas"),
    VENTA_CREAR("Ventas", "Crear ventas"),
    VENTA_ANULAR("Ventas", "Anular ventas");

    private final String modulo;
    private final String descripcion;
}