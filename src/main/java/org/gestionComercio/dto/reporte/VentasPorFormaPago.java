package org.gestionComercio.dto.reporte;

import org.gestionComercio.enums.FormaPago;

import java.math.BigDecimal;

/**
 * Ventas cobradas con una forma de pago, sin las anuladas ni las canceladas.
 *
 * @param formaPago forma de pago
 * @param cantidad  cantidad de ventas
 * @param total     suma de los totales
 */
public record VentasPorFormaPago(FormaPago formaPago, Long cantidad, BigDecimal total) {

    public VentasPorFormaPago {
        cantidad = cantidad == null ? 0L : cantidad;
        total = total == null ? BigDecimal.ZERO : total;
    }
}
