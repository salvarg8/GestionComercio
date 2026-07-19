package org.gestionComercio.repository;

import org.gestionComercio.entity.Producto;
import org.gestionComercio.enums.EstadoRegistro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends BaseRepository<Producto, Long> {

    Optional<Producto> findByCodigo(String codigo);

    Optional<Producto> findByCodigoBarras(String codigoBarras);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    boolean existsByCodigoBarras(String codigoBarras);

    boolean existsByCodigoBarrasAndIdNot(String codigoBarras, Long id);

    List<Producto> findByNombreContainingIgnoreCase(String nombre);

    List<Producto> findByEstadoRegistro(EstadoRegistro estadoRegistro);

    Optional<Producto> findByIdAndEstadoRegistro(Long id, EstadoRegistro estadoRegistro);

    boolean existsByCodigoAndEstadoRegistro(String codigo, EstadoRegistro estadoRegistro);

    boolean existsByCodigoAndIdNotAndEstadoRegistro(
            String codigo,
            Long id,
            EstadoRegistro estadoRegistro);
}
