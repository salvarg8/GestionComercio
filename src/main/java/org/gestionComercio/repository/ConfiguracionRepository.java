package org.gestionComercio.repository;

import org.gestionComercio.entity.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfiguracionRepository extends JpaRepository<Configuracion, Long> {

    /** Hay un único registro de configuración: se toma el primero. */
    Optional<Configuracion> findFirstByOrderByIdAsc();

}
