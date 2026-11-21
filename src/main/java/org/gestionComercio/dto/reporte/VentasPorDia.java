package org.gestionComercio.dto.reporte;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ventas de un día, sin las anuladas ni las canceladas.
 *
 * @param fecha     día
 * @param cantidad  cantidad de ventas
 * @param subtotal  suma de los subtotales (antes del descuento)
 * @param descuento suma de los descuentos
 * @param total     suma de los totales
 */
public record VentasPorDia(
        LocalDate fecha,
        Long cantidad,
        BigDecimal subtotal,
        BigDecimal descuento,
        BigDecimal total) {

    public VentasPorDia {
        cantidad = cantidad == null ? 0L : cantidad;
        subtotal = subtotal == null ? BigDecimal.ZERO : subtotal;
        descuento = descuento == null ? BigDecimal.ZERO : descuento;
        total = total == null ? BigDecimal.ZERO : total;
    }
}
