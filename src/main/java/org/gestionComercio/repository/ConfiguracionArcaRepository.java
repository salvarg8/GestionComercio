package org.gestionComercio.repository;

import org.gestionComercio.entity.ConfiguracionArca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracionArcaRepository extends JpaRepository<ConfiguracionArca, Long> {

    /** Hay un único registro de configuración: se toma el primero. */
    Optional<ConfiguracionArca> findFirstByOrderByIdAsc();

}
