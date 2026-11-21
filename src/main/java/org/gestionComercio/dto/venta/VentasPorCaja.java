package org.gestionComercio.dto.venta;

import org.gestionComercio.enums.EstadoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lo vendido en una caja durante un período, sin contar las ventas anuladas
 * ni las canceladas.
 *
 * @param cajaId                ID de la caja
 * @param usuarioNombreCompleto nombre y apellido del responsable de la caja
 * @param estadoCaja            estado actual de la caja
 * @param fechaApertura         cuándo se abrió la caja
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
}
