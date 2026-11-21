package org.gestionComercio.dto.venta;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Totales de las ventas de un período, sin contar las anuladas ni las canceladas.
 *
 * @param cantidad cantidad de ventas
 * @param total    suma de sus totales (cero si no hay ventas)
 */
public record ResumenVentas(long cantidad, BigDecimal total) {

    public ResumenVentas {
        total = total == null ? BigDecimal.ZERO : total;
    }

    /** Suma lo vendido en las cajas indicadas. */
    public static ResumenVentas de(Collection<VentasPorCaja> cajas) {

        long cantidad = 0;
        BigDecimal total = BigDecimal.ZERO;

        for (VentasPorCaja caja : cajas) {
            cantidad += caja.cantidad();
            total = total.add(caja.total());
        }

        return new ResumenVentas(cantidad, total);
    }
}
