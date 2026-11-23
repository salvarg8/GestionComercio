package org.gestionComercio.dto.venta;

import org.gestionComercio.enums.EstadoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lo vendido en una caja durante un período, sin contar las ventas anuladas
 * ni las canceladas.
 *
 * @param cajaId                ID de la caja; null para lo vendido sin caja
 * @param usuarioNombreCompleto nombre y apellido del responsable de la caja (o del vendedor, sin caja)
 * @param estadoCaja            estado actual de la caja; null sin caja
 * @param fechaApertura         cuándo se abrió la caja; null sin caja
 * @param cantidad              cantidad de ventas
 * @param total                 suma de sus totales
 */
public record VentasPorCaja(
        Long cajaId,
        String usuarioNombreCompleto,
        EstadoCaja estadoCaja,
        LocalDateTime fechaApertura,
        Long cantidad,
        BigDecimal total) {

    public VentasPorCaja {
        cantidad = cantidad == null ? 0L : cantidad;
        total = total == null ? BigDecimal.ZERO : total;
    }

    /** Lo vendido sin caja por un vendedor (si la configuración no exige caja abierta). */
    public VentasPorCaja(String usuarioNombreCompleto, Long cantidad, BigDecimal total) {
        this(null, usuarioNombreCompleto, null, null, cantidad, total);
    }

    /** @return true si son ventas registradas sin caja */
    public boolean sinCaja() {
        return cajaId == null;
    }
}
