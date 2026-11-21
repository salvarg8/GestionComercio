package org.gestionComercio.dto.reporte;

import org.gestionComercio.enums.UnidadMedida;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Stock actual de un producto y su precio de compra, para valorizar el inventario.
 *
 * @param productoId      ID del producto
 * @param codigo          código interno
 * @param nombre          nombre
 * @param categoriaNombre categoría
 * @param unidadMedida    unidad del stock
 * @param stock           stock actual
 * @param precioCompra    precio de compra actual
 */
public record ProductoValorizado(
        Long productoId,
        String codigo,
        String nombre,
        String categoriaNombre,
        UnidadMedida unidadMedida,
        BigDecimal stock,
        BigDecimal precioCompra) {

    public ProductoValorizado {
        stock = stock == null ? BigDecimal.ZERO : stock;
        precioCompra = precioCompra == null ? BigDecimal.ZERO : precioCompra;
    }

    /** Stock × precio de compra, redondeado a centavos. */
    public BigDecimal valor() {
        return stock.multiply(precioCompra).setScale(2, RoundingMode.HALF_UP);
    }
}
