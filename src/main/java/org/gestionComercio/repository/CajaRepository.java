package org.gestionComercio.repository;

import org.gestionComercio.entity.Caja;
import org.gestionComercio.enums.EstadoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CajaRepository extends JpaRepository<Caja, Long> {

    boolean existsByUsuarioIdAndEstadoCaja(Long usuarioId, EstadoCaja estadoCaja);

    /** Indica si se cerró alguna caja después de la fecha indicada (backup automático). */
    boolean existsByFechaCierreAfter(LocalDateTime fecha);

    Optional<Caja> findFirstByUsuarioIdAndEstadoCajaOrderByFechaAperturaDesc(Long usuarioId, EstadoCaja estadoCaja);

    /** Cajas abiertas de todos los usuarios, ordenadas por responsable. */
    @Query("""
            select c from Caja c
              join fetch c.usuario u
              join fetch c.usuarioApertura
            where c.estadoCaja = org.gestionComercio.enums.EstadoCaja.ABIERTA
            order by u.apellido, u.nombre, c.id
            """)
    List<Caja> findAbiertas();

    /**
     * Cajas cerradas, de la más reciente a la más antigua. Cada fecha null no
     * filtra; ambas incluyen el extremo.
     */
    @Query("""
            select c from Caja c
              join fetch c.usuario
              join fetch c.usuarioApertura
              left join fetch c.usuarioCierre
            where c.estadoCaja = org.gestionComercio.enums.EstadoCaja.CERRADA
              and (cast(:desde as LocalDateTime) is null or c.fechaCierre >= :desde)
              and (cast(:hasta as LocalDateTime) is null or c.fechaCierre <= :hasta)
            order by c.fechaCierre desc, c.id desc
            """)
    List<Caja> findCerradas(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

}
