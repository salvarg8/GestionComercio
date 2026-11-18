package org.gestionComercio.repository;

import org.gestionComercio.entity.Cliente;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.TipoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Busca por nombre, apellido (en cualquier orden) o número de documento.
     *
     * @param texto     parte del nombre y/o apellido ("" = sin filtro de texto)
     * @param documento caracteres a buscar dentro del documento ("" = no buscar por documento)
     * @param estado    estado del registro (null = todos)
     */
    @Query("""
            select c from Cliente c
            where (:estado is null or c.estadoRegistro = :estado)
              and (lower(concat(coalesce(c.apellido, ''), ' ', c.nombre))
                       like lower(concat('%', :texto, '%'))
                   or lower(concat(c.nombre, ' ', coalesce(c.apellido, '')))
                       like lower(concat('%', :texto, '%'))
                   or (:documento <> '' and c.numeroDocumento like concat('%', :documento, '%')))
            order by lower(coalesce(c.apellido, c.nombre)) asc, lower(c.nombre) asc
            """)
    List<Cliente> buscar(
            @Param("texto") String texto,
            @Param("documento") String documento,
            @Param("estado") EstadoRegistro estado);

    Optional<Cliente> findByIdAndEstadoRegistro(Long id, EstadoRegistro estadoRegistro);

    long countByEstadoRegistro(EstadoRegistro estadoRegistro);

    /*
     * Las búsquedas por documento no filtran por estado a propósito: tipo y número
     * son UNIQUE en la base, así que un cliente dado de baja sigue ocupando su documento.
     */
    Optional<Cliente> findByTipoDocumentoAndNumeroDocumento(
            TipoDocumento tipoDocumento, String numeroDocumento);

    Optional<Cliente> findByTipoDocumentoAndNumeroDocumentoAndIdNot(
            TipoDocumento tipoDocumento, String numeroDocumento, Long id);
}
