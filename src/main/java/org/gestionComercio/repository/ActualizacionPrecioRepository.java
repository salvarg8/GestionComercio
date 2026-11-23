package org.gestionComercio.repository;

import org.gestionComercio.entity.ActualizacionPrecio;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ActualizacionPrecioRepository extends JpaRepository<ActualizacionPrecio, Long> {

    /** Las más recientes primero, con el usuario que las aplicó. */
    @Query("""
            select a from ActualizacionPrecio a
              left join fetch a.usuarioCreacion
            order by a.fechaCreacion desc, a.id desc
            """)
    List<ActualizacionPrecio> buscarRecientes(Pageable pagina);

    /** Una actualización con sus renglones y los productos. */
    @Query("""
            select distinct a from ActualizacionPrecio a
              left join fetch a.usuarioCreacion
              left join fetch a.detalles d
              left join fetch d.producto
            where a.id = :id
            """)
    Optional<ActualizacionPrecio> findConDetalles(@Param("id") Long id);

}
