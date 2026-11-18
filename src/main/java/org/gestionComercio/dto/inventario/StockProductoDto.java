package org.gestionComercio.dto.inventario;

import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.SituacionStock;
import org.gestionComercio.enums.UnidadMedida;

import java.math.BigDecimal;

/**
 * Existencias actuales de un producto, para la consulta de stock. Se arma
 * directamente en la consulta JPQL ({@code ProductoRepository}), sin cargar
 * la entidad completa.
 */
public record StockProductoDto(
        Long productoId,
        String codigo,
        String codigoBarras,
        String nombre,
        Long categoriaId,
        String categoriaNombre,
        String marcaNombre,
        UnidadMedida unidadMedida,
        BigDecimal stock,
        BigDecimal stockMinimo,
        Boolean manejaStock,
        EstadoRegistro estadoRegistro) {

    public SituacionStock situacion() {
        return SituacionStock.de(manejaStock, stock, stockMinimo);
    }

    public boolean isActivo() {
        return estadoRegistro == null || estadoRegistro == EstadoRegistro.ACTIVO;
    }
}
