package org.gestionComercio.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PermisoCodigo {

    USUARIO_VER("Ver usuarios"),
    USUARIO_CREAR("Crear usuarios"),
    USUARIO_EDITAR("Editar usuarios"),
    USUARIO_ELIMINAR("Eliminar usuarios"),

    ROL_VER("Ver roles"),
    ROL_CREAR("Crear roles"),
    ROL_EDITAR("Editar roles"),
    ROL_ELIMINAR("Eliminar roles"),

    EMPRESA_VER("Ver empresas"),
    EMPRESA_EDITAR("Editar empresas"),

    CLIENTE_VER("Ver clientes"),
    CLIENTE_CREAR("Crear clientes"),
    CLIENTE_EDITAR("Editar clientes"),
    CLIENTE_ELIMINAR("Eliminar clientes"),

    PRODUCTO_VER("Ver productos"),
    PRODUCTO_CREAR("Crear productos"),
    PRODUCTO_EDITAR("Editar productos"),
    PRODUCTO_ELIMINAR("Eliminar productos"),

    VENTA_VER("Ver ventas"),
    VENTA_CREAR("Crear ventas"),
    VENTA_ANULAR("Anular ventas");

    private final String descripcion;
}