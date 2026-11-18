package org.gestionComercio.dto.inventario;

import org.gestionComercio.enums.TipoMovimientoInventario;

import java.time.LocalDate;

/**
 * Criterios de la consulta del historial de movimientos de inventario. Todos
 * son opcionales: un criterio null no filtra.
 *
 * @param productoId producto (null = todos)
 * @param tipo       tipo de movimiento (null = todos)
 * @param desde      fecha desde, incluida (null = sin límite)
 * @param hasta      fecha hasta, incluida (null = sin límite)
 */
public record FiltroMovimientos(
        Long productoId,
        TipoMovimientoInventario tipo,
        LocalDate desde,
        LocalDate hasta) {

    /** Sin filtros: todos los movimientos registrados. */
    public static FiltroMovimientos todos() {
        return new FiltroMovimientos(null, null, null, null);
    }

    /** Todos los movimientos de un producto. */
    public static FiltroMovimientos deProducto(Long productoId) {
        return new FiltroMovimientos(productoId, null, null, null);
    }

}
