package org.gestionComercio.repository;

import jakarta.persistence.LockModeType;
import org.gestionComercio.entity.Venta;
import org.gestionComercio.enums.EstadoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {

    /** Una venta con su cliente, vendedor, caja y renglones (con sus productos). */
    @Query("""
            select v from Venta v
              left join fetch v.cliente
              join fetch v.usuario
              join fetch v.caja
              left join fetch v.usuarioAnulacion
              left join fetch v.detalles d
              left join fetch d.producto
            where v.id = :id
            """)
    Optional<Venta> findConDetalles(@Param("id") Long id);

    /**
     * Una venta bloqueada para modificarla (SELECT ... FOR UPDATE) hasta que
     * termine la transacción: dos anulaciones simultáneas de la misma venta
     * se ejecutan una después de la otra, y la segunda la ve ya anulada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> findParaModificar(@Param("id") Long id);

    /**
     * Ventas de la más reciente a la más antigua, sin sus renglones. Cada
     * filtro null no filtra; las fechas incluyen el extremo.
     */
    @Query("""
            select v from Venta v
              left join fetch v.cliente
              join fetch v.usuario
              join fetch v.caja
              left join fetch v.usuarioAnulacion
            where (cast(:desde as LocalDateTime) is null or v.fecha >= :desde)
              and (cast(:hasta as LocalDateTime) is null or v.fecha <= :hasta)
              and (:estado is null or v.estadoVenta = :estado)
            order by v.fecha desc, v.id desc
            """)
    List<Venta> buscar(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("estado") EstadoVenta estado);

}
