package org.gestionComercio.repository;

import org.gestionComercio.entity.Producto;
import org.gestionComercio.enums.EstadoRegistro;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends BaseRepository<Producto, Long> {

    /**
     * Busca por nombre, código interno o código de barras. Trae la categoría,
     * la marca y el proveedor en la misma consulta para mostrarlos en el listado.
     *
     * @param texto  parte del nombre o del código, o el código de barras completo
     *               ("" = sin filtro de texto)
     * @param estado estado del registro (null = todos)
     */
    @Query("""
            select p from Producto p
              join fetch p.categoria
              join fetch p.marca
              join fetch p.proveedor
            where (:estado is null or p.estadoRegistro = :estado)
              and (:texto = ''
                   or lower(p.nombre) like lower(concat('%', :texto, '%'))
                   or lower(p.codigo) like lower(concat('%', :texto, '%'))
                   or p.codigoBarras = :texto)
            order by p.nombre asc
            """)
    List<Producto> buscar(
            @Param("texto") String texto,
            @Param("estado") EstadoRegistro estado);

    Optional<Producto> findByIdAndEstadoRegistro(Long id, EstadoRegistro estadoRegistro);

    long countByEstadoRegistro(EstadoRegistro estadoRegistro);

    /*
     * Las búsquedas por código no filtran por estado a propósito: las columnas
     * codigo y codigo_barras son UNIQUE en la base, así que un producto dado de
     * baja sigue ocupando sus códigos.
     */
    Optional<Producto> findFirstByCodigoIgnoreCase(String codigo);

    Optional<Producto> findFirstByCodigoIgnoreCaseAndIdNot(String codigo, Long id);

    Optional<Producto> findByCodigoBarras(String codigoBarras);

    Optional<Producto> findByCodigoBarrasAndIdNot(String codigoBarras, Long id);

    boolean existsByCategoriaIdAndEstadoRegistro(Long categoriaId, EstadoRegistro estadoRegistro);
}
