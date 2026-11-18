package org.gestionComercio.repository;

import org.gestionComercio.entity.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Long> {

    /** Movimientos de una caja en el orden en que se registraron. */
    List<MovimientoCaja> findByCajaIdOrderByFechaAscIdAsc(Long cajaId);

}
