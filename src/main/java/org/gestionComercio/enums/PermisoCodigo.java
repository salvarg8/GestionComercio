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
    PRODUCTO_ELIMINAR("Productos", "Eliminar productos"),

    // Ventas
    VENTA_VER("Ventas", "Ver ventas"),
    VENTA_CREAR("Ventas", "Crear ventas"),
    VENTA_ANULAR("Ventas", "Anular ventas");

    private final String modulo;
    private final String descripcion;
}