package org.gestionComercio.service;

import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.enums.FiltroEstado;

import java.util.List;
import java.util.Optional;

/**
 * Los productos no se borran de la base: se dan de baja y se pueden volver a
 * habilitar. Así las ventas, compras y movimientos de stock que los referencian
 * se conservan intactos.
 */
public interface ProductoService extends CrudService<ProductoDto, Long> {

    /**
     * Busca por nombre, código interno o código de barras (texto vacío = todos)
     * entre los productos del estado indicado.
     */
    List<ProductoDto> buscar(String texto, FiltroEstado filtro);

    /** Requiere PRODUCTO_DAR_BAJA. */
    void darDeBaja(Long id);

    /**
     * Requiere PRODUCTO_HABILITAR. Falla si la categoría del producto fue
     * eliminada: hay que editarlo y asignarle una categoría vigente primero.
     */
    void habilitar(Long id);

    /**
     * Equivale a {@link #darDeBaja(Long)}: los productos nunca se borran de la base.
     */
    @Override
    void delete(Long id);

    /**
     * Devuelve el producto esté activo o dado de baja (ver {@link ProductoDto#isActivo()}),
     * para poder corregir uno dado de baja antes de habilitarlo.
     */
    @Override
    Optional<ProductoDto> findById(Long id);

}
