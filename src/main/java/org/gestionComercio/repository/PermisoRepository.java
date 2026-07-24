package org.gestionComercio.repository;

import org.gestionComercio.entity.Permiso;
import org.gestionComercio.enums.PermisoCodigo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {

    Optional<Permiso> findByCodigo(PermisoCodigo codigo);

    boolean existsByCodigo(PermisoCodigo codigo);

    List<Permiso> findAllByOrderByCodigoAsc();

}