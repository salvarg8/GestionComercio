package org.gestionComercio.repository;

import org.gestionComercio.entity.Caja;
import org.gestionComercio.enums.EstadoCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CajaRepository extends JpaRepository<Caja, Long> {

    boolean existsByUsuarioIdAndEstadoCaja(Long usuarioId, EstadoCaja estadoCaja);

    Optional<Caja> findFirstByUsuarioIdAndEstadoCajaOrderByFechaAperturaDesc(Long usuarioId, EstadoCaja estadoCaja);

}
