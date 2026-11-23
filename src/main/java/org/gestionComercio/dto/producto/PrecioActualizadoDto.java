package org.gestionComercio.dto.producto;

import java.math.BigDecimal;

/**
 * Precios de un producto antes y después de una actualización masiva: la
 * propuesta de la vista previa o lo que quedó registrado al aplicarla.
 */
public record PrecioActualizadoDto(
        Long productoId,
        String codigo,
        String nombre,
        String categoria,
        String marca,
        String proveedor,
        BigDecimal precioCompraAnterior,
        BigDecimal precioCompraNuevo,
        BigDecimal precioVentaAnterior,
        BigDecimal precioVentaNuevo) {
}
