package org.gestionComercio.dto.inventario;

import java.math.BigDecimal;

/**
 * Un renglón de un comprobante que mueve stock: el producto y cuánto entra o
 * sale (siempre positivo; el sentido lo da la operación).
 */
public record LineaInventario(Long productoId, BigDecimal cantidad) {
}
