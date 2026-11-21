package org.gestionComercio.dto.reporte;

import org.gestionComercio.enums.UnidadMedida;

import java.math.BigDecimal;

/**
 * Lo vendido de un producto, sin contar las ventas anuladas ni las canceladas.
 *
 * @param productoId   ID del producto
 * @param codigo       código interno
 * @param nombre       nombre
 * @param unidadMedida unidad en la que se vende
 * @param cantidad     cantidad vendida
 * @param importe      suma de los subtotales de sus renglones (antes del descuento general de la venta)
 */
public record ProductoVendido(
        Long productoId,
        String codigo,
        String nombre,
        UnidadMedida unidadMedida,
        BigDecimal cantidad,
        BigDecimal importe) {

    public ProductoVendido {
        cantidad = cantidad == null ? BigDecimal.ZERO : cantidad;
        importe = importe == null ? BigDecimal.ZERO : importe;
    }
}
