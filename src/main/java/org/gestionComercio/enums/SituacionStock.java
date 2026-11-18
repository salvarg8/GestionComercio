package org.gestionComercio.enums;

import java.math.BigDecimal;

/**
 * Situación del stock de un producto respecto de su stock mínimo.
 *
 * <p>Reglas (en este orden):
 * <ol>
 *     <li>{@link #NO_MANEJA_STOCK}: el producto no lleva control de stock.</li>
 *     <li>{@link #SIN_STOCK}: stock en cero o negativo.</li>
 *     <li>{@link #STOCK_BAJO}: stock en el mínimo o por debajo (la misma regla
 *         que {@code ProductoDto.isStockBajo()}).</li>
 *     <li>{@link #NORMAL}: por encima del mínimo.</li>
 * </ol>
 */
public enum SituacionStock implements Descriptible {

    SIN_STOCK("Sin stock"),
    STOCK_BAJO("Stock bajo"),
    NORMAL("Normal"),
    NO_MANEJA_STOCK("No maneja stock");

    private final String descripcion;

    SituacionStock(String descripcion) {
        this.descripcion = descripcion;
    }

    /**
     * @param manejaStock  null se toma como que no maneja stock
     * @param stock        null se toma como cero
     * @param stockMinimo  null se toma como cero
     */
    public static SituacionStock de(Boolean manejaStock, BigDecimal stock, BigDecimal stockMinimo) {

        if (!Boolean.TRUE.equals(manejaStock)) {
            return NO_MANEJA_STOCK;
        }

        BigDecimal actual = stock == null ? BigDecimal.ZERO : stock;
        BigDecimal minimo = stockMinimo == null ? BigDecimal.ZERO : stockMinimo;

        if (actual.signum() <= 0) {
            return SIN_STOCK;
        }

        return actual.compareTo(minimo) <= 0 ? STOCK_BAJO : NORMAL;
    }

    /** @return true si hay que reponer: sin stock o con stock bajo. */
    public boolean requiereReposicion() {
        return this == SIN_STOCK || this == STOCK_BAJO;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
