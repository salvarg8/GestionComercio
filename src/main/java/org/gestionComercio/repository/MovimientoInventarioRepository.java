package org.gestionComercio.repository;

import org.gestionComercio.entity.MovimientoInventario;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Extiende {@link Repository} y no {@code JpaRepository} a propósito: sólo
 * expone alta y consultas, así no hay forma de borrar movimientos ni de
 * guardarlos en lote. Modificar uno ya guardado lo impide la propia entidad.
 */
@org.springframework.stereotype.Repository
public interface MovimientoInventarioRepository extends Repository<MovimientoInventario, Long> {

    MovimientoInventario save(MovimientoInventario movimiento);

    Optional<MovimientoInventario> findById(Long id);

    /** Historial de un producto, del más reciente al más antiguo. */
    @Query("""
            select m from MovimientoInventario m
              join fetch m.producto
              join fetch m.usuario
            where m.producto.id = :productoId
            order by m.fecha desc, m.id desc
            """)
    List<MovimientoInventario> findHistorialPorProducto(@Param("productoId") Long productoId);

    /**
     * Movimientos entre dos fechas (ambas incluidas), del más reciente al más
     * antiguo. Con productoId null trae los de todos los productos.
     */
    @Query("""
            select m from MovimientoInventario m
              join fetch m.producto
              join fetch m.usuario
            where m.fecha between :desde and :hasta
              and (:productoId is null or m.producto.id = :productoId)
            order by m.fecha desc, m.id desc
            """)
    List<MovimientoInventario> buscar(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("productoId") Long productoId);

    /** Último movimiento del producto, para controlar que el historial esté alineado con su stock. */
    Optional<MovimientoInventario> findFirstByProductoIdOrderByFechaDescIdDesc(Long productoId);

    long countByProductoId(Long productoId);

    /** true si el comprobante ya registró sus movimientos originales (sin contar reversiones). */
    boolean existsByOrigenAndOrigenIdAndMovimientoRevertidoIsNull(
            OrigenMovimientoInventario origen, Long origenId);

    /** true si los movimientos del comprobante ya fueron revertidos. */
    boolean existsByOrigenAndOrigenIdAndMovimientoRevertidoIsNotNull(
            OrigenMovimientoInventario origen, Long origenId);

    /** Movimientos originales de un comprobante (sin las reversiones), en el orden en que se registraron. */
    @Query("""
            select m from MovimientoInventario m
              join fetch m.producto
            where m.origen = :origen
              and m.origenId = :origenId
              and m.movimientoRevertido is null
            order by m.id asc
            """)
    List<MovimientoInventario> findOriginalesDelComprobante(
            @Param("origen") OrigenMovimientoInventario origen,
            @Param("origenId") Long origenId);

}
