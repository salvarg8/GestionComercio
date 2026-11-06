package org.gestionComercio.repository;

import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    @EntityGraph(attributePaths = {"rol", "rol.permisos"})
    Optional<Usuario> findByUsuario(String usuario);

    @EntityGraph(attributePaths = {"rol"})
    List<Usuario> findByEstadoRegistroOrderByApellidoAscNombreAsc(EstadoRegistro estadoRegistro);

    @EntityGraph(attributePaths = {"rol"})
    @Query("""
            select u from Usuario u
            where u.estadoRegistro = :estado
              and (lower(u.usuario) like lower(concat('%', :texto, '%'))
                or lower(u.nombre) like lower(concat('%', :texto, '%'))
                or lower(u.apellido) like lower(concat('%', :texto, '%'))
                or u.documento like concat('%', :texto, '%'))
            order by u.apellido, u.nombre
            """)
    List<Usuario> buscar(@Param("texto") String texto,
                         @Param("estado") EstadoRegistro estado);

    boolean existsByUsuarioIgnoreCase(String usuario);

    boolean existsByUsuarioIgnoreCaseAndIdNot(String usuario, Long id);

    boolean existsByDocumento(String documento);

    boolean existsByDocumentoAndIdNot(String documento, Long id);
}
