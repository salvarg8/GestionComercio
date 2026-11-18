package org.gestionComercio.enums;

import java.math.BigDecimal;

/**
 * Tipo de un movimiento de inventario. Define qué signo puede tener la cantidad
 * del movimiento (la variación de stock que produce).
 */
public enum TipoMovimientoInventario implements Descriptible {

    /** Ingreso de mercadería (compra, devolución de un cliente, etc.). Cantidad positiva. */
    ENTRADA("Entrada"),

    /** Egreso de mercadería (venta, devolución a un proveedor, etc.). Cantidad negativa. */
    SALIDA("Salida"),

    /** Corrección por recuento, rotura, vencimiento, etc. Cantidad positiva o negativa. */
    AJUSTE("Ajuste");

    private final String descripcion;

    TipoMovimientoInventario(String descripcion) {
        this.descripcion = descripcion;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    /** @return true si una variación de stock con este signo es válida para el tipo. */
    public boolean admiteCantidad(BigDecimal cantidad) {

        if (cantidad == null) {
            return false;
        }

        return switch (this) {
            case ENTRADA -> cantidad.signum() > 0;
            case SALIDA -> cantidad.signum() < 0;
            case AJUSTE -> cantidad.signum() != 0;
        };
    }

    /** Tipo del movimiento que revierte a uno de este tipo. */
    public TipoMovimientoInventario opuesto() {
        return switch (this) {
            case ENTRADA -> SALIDA;
            case SALIDA -> ENTRADA;
            case AJUSTE -> AJUSTE;
        };
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
