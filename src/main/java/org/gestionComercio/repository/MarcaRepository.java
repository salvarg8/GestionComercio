package org.gestionComercio.repository;

import org.gestionComercio.entity.Marca;
import org.gestionComercio.enums.EstadoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MarcaRepository extends JpaRepository<Marca, Long> {

    List<Marca> findByEstadoRegistroOrderByNombreAsc(EstadoRegistro estadoRegistro);

    List<Marca> findByNombreContainingIgnoreCaseAndEstadoRegistroOrderByNombreAsc(
            String nombre,
            EstadoRegistro estadoRegistro);

    List<Marca> findAllByOrderByNombreAsc();

    List<Marca> findByNombreContainingIgnoreCaseOrderByNombreAsc(String nombre);

    Optional<Marca> findByIdAndEstadoRegistro(Long id, EstadoRegistro estadoRegistro);

    long countByEstadoRegistro(EstadoRegistro estadoRegistro);

    /*
     * Las búsquedas por nombre no filtran por estado a propósito: la columna
     * nombre es UNIQUE en la base, así que una marca dada de baja (inactiva)
     * sigue ocupando su nombre.
     */
    Optional<Marca> findByNombreIgnoreCase(String nombre);

    Optional<Marca> findByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
