package org.gestionComercio.repository;

import org.gestionComercio.entity.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    /** Movimientos de una caja en el orden en que se registraron. */
    List<MovimientoCaja> findByCajaIdOrderByFechaAscIdAsc(Long cajaId);

    /** Movimientos de varias cajas a la vez, para calcular sus totales con una sola consulta. */
    List<MovimientoCaja> findByCajaIdIn(Collection<Long> cajaIds);

}
