package org.gestionComercio.repository;

import org.gestionComercio.entity.Categoria;
import org.gestionComercio.enums.EstadoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findByEstadoRegistroOrderByNombreAsc(EstadoRegistro estadoRegistro);

    List<Categoria> findByNombreContainingIgnoreCaseAndEstadoRegistroOrderByNombreAsc(
            String nombre,
            EstadoRegistro estadoRegistro);

    Optional<Categoria> findByIdAndEstadoRegistro(Long id, EstadoRegistro estadoRegistro);

    long countByEstadoRegistro(EstadoRegistro estadoRegistro);

    /*
     * Las búsquedas por nombre no filtran por estado a propósito: la columna
     * nombre es UNIQUE en la base, así que una categoría eliminada (inactiva)
     * sigue ocupando su nombre.
     */
    Optional<Categoria> findByNombreIgnoreCase(String nombre);

    Optional<Categoria> findByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
