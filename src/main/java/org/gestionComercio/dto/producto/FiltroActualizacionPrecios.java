package org.gestionComercio.dto.producto;

import org.gestionComercio.enums.PrecioActualizado;
import org.gestionComercio.enums.Redondeo;

import java.math.BigDecimal;

/**
 * Qué productos y cómo actualiza una actualización masiva de precios.
 *
 * @param porcentaje  aumento (positivo) o rebaja (negativa); mayor que -100
 * @param precio      precio que se modifica
 * @param redondeo    redondeo de los precios nuevos (al más cercano); null = sin redondeo
 * @param categoriaId categoría; null = todas
 * @param marcaId     marca; null = todas
 * @param proveedorId proveedor; null = todos
 */
public record FiltroActualizacionPrecios(
        BigDecimal porcentaje,
        PrecioActualizado precio,
        Redondeo redondeo,
        Long categoriaId,
        Long marcaId,
        Long proveedorId) {

    public FiltroActualizacionPrecios {
        redondeo = redondeo == null ? Redondeo.SIN_REDONDEO : redondeo;
    }
}
