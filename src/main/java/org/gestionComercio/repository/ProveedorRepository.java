package org.gestionComercio.repository;

import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.enums.EstadoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {

    /**
     * Busca por razón social o por CUIT.
     *
     * @param texto  parte de la razón social ("" = sin filtro de texto)
     * @param cuit   dígitos a buscar dentro del CUIT ("" = no buscar por CUIT)
     * @param estado estado del registro (null = todos)
     */
    @Query("""
            select p from Proveedor p
            where (:estado is null or p.estadoRegistro = :estado)
              and (lower(p.razonSocial) like lower(concat('%', :texto, '%'))
                   or (:cuit <> '' and p.cuit like concat('%', :cuit, '%')))
            order by p.razonSocial asc
            """)
    List<Proveedor> buscar(
            @Param("texto") String texto,
            @Param("cuit") String cuit,
            @Param("estado") EstadoRegistro estado);

    List<Proveedor> findByEstadoRegistroOrderByRazonSocialAsc(EstadoRegistro estadoRegistro);

    Optional<Proveedor> findByIdAndEstadoRegistro(Long id, EstadoRegistro estadoRegistro);

    long countByEstadoRegistro(EstadoRegistro estadoRegistro);

    /*
     * Las búsquedas por CUIT no filtran por estado a propósito: la columna cuit
     * es UNIQUE en la base, así que un proveedor dado de baja sigue ocupando su CUIT.
     */
    Optional<Proveedor> findByCuit(String cuit);

    Optional<Proveedor> findByCuitAndIdNot(String cuit, Long id);
}
