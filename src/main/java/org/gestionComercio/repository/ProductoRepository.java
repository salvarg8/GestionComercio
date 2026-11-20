package org.gestionComercio.repository;

import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.enums.EstadoRegistro;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends BaseRepository<Producto, Long> {

    /**
     * Busca por nombre, código interno o código de barras. Trae la categoría,
     * la marca y el proveedor en la misma consulta para mostrarlos en el listado.
     *
     * @param texto  parte del nombre o del código, o el código de barras completo
     *               ("" = sin filtro de texto)
     * @param estado estado del registro (null = todos)
     */
    @Query("""
            select p from Producto p
              join fetch p.categoria
              join fetch p.marca
              join fetch p.proveedor
            where (:estado is null or p.estadoRegistro = :estado)
              and (:texto = ''
                   or lower(p.nombre) like lower(concat('%', :texto, '%'))
                   or lower(p.codigo) like lower(concat('%', :texto, '%'))
                   or p.codigoBarras = :texto)
            order by p.nombre asc
            """)
    List<Producto> buscar(
            @Param("texto") String texto,
            @Param("estado") EstadoRegistro estado);

    Optional<Producto> findByIdAndEstadoRegistro(Long id, EstadoRegistro estadoRegistro);

    long countByEstadoRegistro(EstadoRegistro estadoRegistro);

    /*
     * El código interno (sin distinguir mayúsculas) y el código de barras son
     * únicos sólo entre los productos activos: un producto dado de baja libera
     * sus códigos. Por eso las búsquedas por código filtran por estado; entre
     * los dados de baja puede haber varios con el mismo código.
     */
    Optional<Producto> findFirstByCodigoIgnoreCaseAndEstadoRegistro(String codigo, EstadoRegistro estadoRegistro);

    Optional<Producto> findFirstByCodigoIgnoreCaseAndEstadoRegistroAndIdNot(
            String codigo, EstadoRegistro estadoRegistro, Long id);

    Optional<Producto> findFirstByCodigoBarrasAndEstadoRegistro(String codigoBarras, EstadoRegistro estadoRegistro);

    Optional<Producto> findFirstByCodigoBarrasAndEstadoRegistroAndIdNot(
            String codigoBarras, EstadoRegistro estadoRegistro, Long id);

    boolean existsByCategoriaIdAndEstadoRegistro(Long categoriaId, EstadoRegistro estadoRegistro);

    // ------------------------------------------------------------------
    // Consulta de stock
    // ------------------------------------------------------------------

    /**
     * Existencias de los productos activos, ordenadas por nombre. Las reglas de
     * "stock bajo" y "sin stock" son las de {@code SituacionStock}.
     *
     * @param texto       parte del nombre o del código, o el código de barras completo
     *                    ("" = sin filtro de texto)
     * @param categoriaId categoría (null = todas)
     * @param filtro      nombre de un {@code FiltroStock}: TODOS, STOCK_BAJO o SIN_STOCK
     */
    @Query("""
            select new org.gestionComercio.dto.inventario.StockProductoDto(
                p.id, p.codigo, p.codigoBarras, p.nombre,
                c.id, c.nombre, m.nombre,
                p.unidadMedida, p.stock, p.stockMinimo, p.manejaStock, p.permiteStockNegativo, p.estadoRegistro)
            from Producto p
              join p.categoria c
              join p.marca m
            where p.estadoRegistro = org.gestionComercio.enums.EstadoRegistro.ACTIVO
              and (:categoriaId is null or c.id = :categoriaId)
              and (:texto = ''
                   or lower(p.nombre) like lower(concat('%', :texto, '%'))
                   or lower(p.codigo) like lower(concat('%', :texto, '%'))
                   or p.codigoBarras = :texto)
              and (:filtro = 'TODOS'
                   or (:filtro = 'STOCK_BAJO' and p.manejaStock = true
                       and (p.stock <= 0 or p.stock <= p.stockMinimo))
                   or (:filtro = 'SIN_STOCK' and p.manejaStock = true and p.stock <= 0))
            order by p.nombre asc
            """)
    List<StockProductoDto> consultarStock(
            @Param("texto") String texto,
            @Param("categoriaId") Long categoriaId,
            @Param("filtro") String filtro);

    /** Existencias de un producto, esté activo o dado de baja. */
    @Query("""
            select new org.gestionComercio.dto.inventario.StockProductoDto(
                p.id, p.codigo, p.codigoBarras, p.nombre,
                c.id, c.nombre, m.nombre,
                p.unidadMedida, p.stock, p.stockMinimo, p.manejaStock, p.permiteStockNegativo, p.estadoRegistro)
            from Producto p
              join p.categoria c
              join p.marca m
            where p.id = :id
            """)
    Optional<StockProductoDto> consultarStockDeProducto(@Param("id") Long id);

    /**
     * Existencias de los productos (activos o dados de baja) que tienen al menos
     * un movimiento de inventario, ordenadas por nombre.
     */
    @Query("""
            select new org.gestionComercio.dto.inventario.StockProductoDto(
                p.id, p.codigo, p.codigoBarras, p.nombre,
                c.id, c.nombre, m.nombre,
                p.unidadMedida, p.stock, p.stockMinimo, p.manejaStock, p.permiteStockNegativo, p.estadoRegistro)
            from Producto p
              join p.categoria c
              join p.marca m
            where exists (select 1 from MovimientoInventario mi where mi.producto = p)
            order by p.nombre asc
            """)
    List<StockProductoDto> consultarStockConMovimientos();

    /** Productos activos que manejan stock. */
    @Query("""
            select count(p) from Producto p
            where p.estadoRegistro = org.gestionComercio.enums.EstadoRegistro.ACTIVO
              and p.manejaStock = true
            """)
    long contarConControlDeStock();

    /** Productos activos que manejan stock y están en el mínimo o por debajo (incluye sin stock). */
    @Query("""
            select count(p) from Producto p
            where p.estadoRegistro = org.gestionComercio.enums.EstadoRegistro.ACTIVO
              and p.manejaStock = true
              and (p.stock <= 0 or p.stock <= p.stockMinimo)
            """)
    long contarConStockBajo();

    /** Productos activos que manejan stock y tienen stock en cero o negativo. */
    @Query("""
            select count(p) from Producto p
            where p.estadoRegistro = org.gestionComercio.enums.EstadoRegistro.ACTIVO
              and p.manejaStock = true
              and p.stock <= 0
            """)
    long contarSinStock();
}
