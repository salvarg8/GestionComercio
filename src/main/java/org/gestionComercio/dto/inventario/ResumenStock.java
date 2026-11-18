package org.gestionComercio.dto.inventario;

/**
 * Contadores de la consulta de stock, sobre los productos activos que manejan
 * stock. {@code conStockBajo} incluye a los que no tienen stock, igual que el
 * filtro {@code FiltroStock.STOCK_BAJO}.
 *
 * @param productosConStock productos activos que manejan stock
 * @param conStockBajo      de ellos, los que están en el mínimo o por debajo
 * @param sinStock          de ellos, los que tienen stock en cero o negativo
 */
public record ResumenStock(long productosConStock, long conStockBajo, long sinStock) {
}
