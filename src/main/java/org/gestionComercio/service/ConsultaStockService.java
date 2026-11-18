package org.gestionComercio.service;

import org.gestionComercio.dto.inventario.ResumenStock;
import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.enums.FiltroStock;

import java.util.List;

/**
 * Consultas de las existencias actuales. Sólo lectura: el stock se modifica
 * con {@link InventarioService} o {@link InventarioComprobanteService}.
 *
 * <p>Todas las operaciones requieren INVENTARIO_VER. Las reglas de "stock
 * bajo" y "sin stock" están en {@link org.gestionComercio.enums.SituacionStock}.
 */
public interface ConsultaStockService {

    /**
     * Stock actual de los productos activos, ordenados por nombre.
     *
     * @param texto       parte del nombre o del código, o el código de barras
     *                    completo (null o vacío = sin filtro)
     * @param categoriaId categoría (null = todas)
     * @param filtro      situación a mostrar (null = todos)
     */
    List<StockProductoDto> consultar(String texto, Long categoriaId, FiltroStock filtro);

    /**
     * Stock actual de un producto, esté activo o dado de baja.
     *
     * @throws org.gestionComercio.exception.EntityNotFoundException si no existe
     */
    StockProductoDto consultarProducto(Long productoId);

    /** Contadores de productos con control de stock, con stock bajo y sin stock. */
    ResumenStock resumen();

}
