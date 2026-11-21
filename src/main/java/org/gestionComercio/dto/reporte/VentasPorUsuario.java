package org.gestionComercio.dto.reporte;

import java.math.BigDecimal;

/**
 * Ventas registradas por un usuario (vendedor), sin las anuladas ni las canceladas.
 *
 * @param usuarioId             ID del vendedor
 * @param usuarioNombreCompleto nombre y apellido del vendedor
 * @param cantidad              cantidad de ventas
 * @param total                 suma de los totales
 */
public record VentasPorUsuario(Long usuarioId, String usuarioNombreCompleto, Long cantidad, BigDecimal total) {

    public VentasPorUsuario {
        cantidad = cantidad == null ? 0L : cantidad;
        total = total == null ? BigDecimal.ZERO : total;
    }
}
