package org.gestionComercio.repository;

import org.gestionComercio.entity.TicketAccesoArca;
import org.gestionComercio.enums.AmbienteArca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TicketAccesoArcaRepository extends JpaRepository<TicketAccesoArca, Long> {

    Optional<TicketAccesoArca> findByAmbienteAndCuitAndServicio(AmbienteArca ambiente, String cuit, String servicio);

    @Modifying
    @Query("delete from TicketAccesoArca t")
    void borrarTodos();

}
