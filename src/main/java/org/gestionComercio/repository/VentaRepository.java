package org.gestionComercio.repository;

import org.gestionComercio.entity.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {

    /** Cuenta ventas de cualquier estado (incluidas las anuladas). */
    boolean existsByClienteId(Long clienteId);
}
