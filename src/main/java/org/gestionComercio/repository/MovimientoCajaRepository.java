package org.gestionComercio.repository;

import org.gestionComercio.entity.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    /** Movimientos de una caja en el orden en que se registraron. */
    List<MovimientoCaja> findByCajaIdOrderByFechaAscIdAsc(Long cajaId);

    /** Movimientos de varias cajas a la vez, para calcular sus totales con una sola consulta. */
    List<MovimientoCaja> findByCajaIdIn(Collection<Long> cajaIds);

    /**
     * Ingresos y egresos de todas las cajas en el orden en que se registraron,
     * con la caja, su responsable y quien registró cada movimiento. No incluye
     * APERTURA ni CIERRE, que no son ingresos ni egresos. Cada fecha null no
     * filtra; las fechas incluyen el extremo.
     */
    @Query("""
            select m from MovimientoCaja m
              join fetch m.caja c
              join fetch c.usuario
              join fetch m.usuario
            where (cast(:desde as LocalDateTime) is null or m.fecha >= :desde)
              and (cast(:hasta as LocalDateTime) is null or m.fecha <= :hasta)
              and m.tipo not in (org.gestionComercio.enums.TipoMovimientoCaja.APERTURA,
                                 org.gestionComercio.enums.TipoMovimientoCaja.CIERRE)
            order by m.fecha, m.id
            """)
    List<MovimientoCaja> buscarIngresosYEgresos(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

}
