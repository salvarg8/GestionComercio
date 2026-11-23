package org.gestionComercio.dto.producto;

import org.gestionComercio.enums.PrecioActualizado;
import org.gestionComercio.enums.Redondeo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una actualización masiva de precios ya aplicada (historial de auditoría).
 *
 * @param usuario nombre y apellido de quien la aplicó
 * @param filtro  categoría, marca y proveedor, en texto
 */
public record ActualizacionPrecioDto(
        Long id,
        LocalDateTime fecha,
        String usuario,
        BigDecimal porcentaje,
        PrecioActualizado precio,
        Redondeo redondeo,
        String filtro,
        int cantidadProductos) {
}
