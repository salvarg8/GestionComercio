package org.gestionComercio.dto.reporte;

import java.math.BigDecimal;

/**
 * Compras a un proveedor, sin las anuladas ni las canceladas.
 *
 * @param proveedorId ID del proveedor
 * @param razonSocial razón social
 * @param cuit        CUIT (puede ser null)
 * @param cantidad    cantidad de compras
 * @param total       suma de los totales
 */
public record ComprasPorProveedor(Long proveedorId, String razonSocial, String cuit, Long cantidad, BigDecimal total) {

    public ComprasPorProveedor {
        cantidad = cantidad == null ? 0L : cantidad;
        total = total == null ? BigDecimal.ZERO : total;
    }
}
