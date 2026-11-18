package org.gestionComercio.enums;

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
    public boolean admiteCantidad(int cantidad) {
        return switch (this) {
            case ENTRADA -> cantidad > 0;
            case SALIDA -> cantidad < 0;
            case AJUSTE -> cantidad != 0;
        };
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
