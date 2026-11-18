package org.gestionComercio.repository;

import org.gestionComercio.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
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
              left join fetch v.detalles d
              left join fetch d.producto
            where v.id = :id
            """)
    Optional<Venta> findConDetalles(@Param("id") Long id);

    /**
     * Ventas de la más reciente a la más antigua, sin sus renglones. Cada
     * fecha null no filtra; ambas incluyen el extremo.
     */
    @Query("""
            select v from Venta v
              left join fetch v.cliente
              join fetch v.usuario
              join fetch v.caja
            where (:desde is null or v.fecha >= :desde)
              and (:hasta is null or v.fecha <= :hasta)
            order by v.fecha desc, v.id desc
            """)
    List<Venta> buscar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

}
