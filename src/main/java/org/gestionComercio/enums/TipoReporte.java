package org.gestionComercio.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reportes de gestión disponibles, con el permiso que exige cada uno y si se
 * filtran por rango de fechas. Los de inventario muestran la situación actual.
 */
@Getter
public enum TipoReporte implements Descriptible {

    VENTAS_POR_PERIODO(
            "Ventas por período",
            "Ventas de cada día del período. No incluye las ventas anuladas ni las canceladas.",
            PermisoCodigo.REPORTE_VENTAS, true),

    VENTAS_POR_FORMA_PAGO(
            "Ventas por forma de pago",
            "Ventas del período agrupadas por forma de pago. No incluye las anuladas ni las canceladas.",
            PermisoCodigo.REPORTE_VENTAS, true),

    VENTAS_POR_USUARIO(
            "Ventas por usuario",
            "Ventas del período agrupadas por vendedor. No incluye las anuladas ni las canceladas.",
            PermisoCodigo.REPORTE_VENTAS, true),

    PRODUCTOS_MAS_VENDIDOS(
            "Productos más vendidos",
            "Cantidad vendida de cada producto en el período, de mayor a menor. El importe es la suma de "
                    + "los renglones, antes del descuento general de cada venta. No incluye ventas anuladas "
                    + "ni canceladas.",
            PermisoCodigo.REPORTE_VENTAS, true),

    VALORIZACION_INVENTARIO(
            "Valorización del inventario",
            "Stock actual de los productos activos que manejan stock, multiplicado por su precio de compra. "
                    + "Los productos sin stock no suman.",
            PermisoCodigo.REPORTE_INVENTARIO, false),

    STOCK_BAJO(
            "Productos con stock bajo y sin stock",
            "Productos activos que manejan stock y están en su stock mínimo, por debajo o sin stock.",
            PermisoCodigo.REPORTE_INVENTARIO, false),

    COMPRAS_POR_PROVEEDOR(
            "Compras por proveedor",
            "Compras del período agrupadas por proveedor. No incluye las anuladas ni las canceladas.",
            PermisoCodigo.REPORTE_COMPRAS, true),

    MOVIMIENTOS_CAJA(
            "Movimientos de caja",
            "Ingresos y egresos de efectivo de todas las cajas en el período (sin el saldo de apertura).",
            PermisoCodigo.REPORTE_CAJA, true);

    private final String descripcion;

    /** Qué muestra el reporte y qué deja afuera. */
    private final String detalle;

    private final PermisoCodigo permiso;

    /** true si se filtra por rango de fechas; false si muestra la situación actual. */
    private final boolean usaFechas;

    TipoReporte(String descripcion, String detalle, PermisoCodigo permiso, boolean usaFechas) {
        this.descripcion = descripcion;
        this.detalle = detalle;
        this.permiso = permiso;
        this.usaFechas = usaFechas;
    }

    /** Reportes cuyo permiso cumple la condición, en el orden del enum. */
    public static List<TipoReporte> permitidos(Predicate<PermisoCodigo> tienePermiso) {
        return Arrays.stream(values())
                .filter(tipo -> tienePermiso.test(tipo.permiso))
                .toList();
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
