package org.gestionComercio.repository;

import jakarta.persistence.LockModeType;
import org.gestionComercio.entity.Compra;
import org.gestionComercio.enums.EstadoCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompraRepository extends JpaRepository<Compra, Long> {

    /** Una compra con su proveedor, usuarios, caja y renglones (con sus productos). */
    @Query("""
            select c from Compra c
              join fetch c.proveedor
              join fetch c.usuario
              left join fetch c.caja
              left join fetch c.usuarioAnulacion
              left join fetch c.detalles d
              left join fetch d.producto
            where c.id = :id
            """)
    Optional<Compra> findConDetalles(@Param("id") Long id);

    /**
     * Una compra bloqueada para modificarla (SELECT ... FOR UPDATE) hasta que
     * termine la transacción: dos anulaciones o recepciones simultáneas de la
     * misma compra se ejecutan una después de la otra.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.id = :id")
    Optional<Compra> findParaModificar(@Param("id") Long id);

    /**
     * Compras de la más reciente a la más antigua, sin sus renglones. Cada
     * filtro null no filtra; las fechas incluyen el extremo.
     */
    @Query("""
            select c from Compra c
              join fetch c.proveedor
              join fetch c.usuario
              left join fetch c.caja
              left join fetch c.usuarioAnulacion
            where (cast(:desde as LocalDateTime) is null or c.fecha >= :desde)
              and (cast(:hasta as LocalDateTime) is null or c.fecha <= :hasta)
              and (:estado is null or c.estadoCompra = :estado)
              and (:proveedorId is null or c.proveedor.id = :proveedorId)
            order by c.fecha desc, c.id desc
            """)
    List<Compra> buscar(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("estado") EstadoCompra estado,
            @Param("proveedorId") Long proveedorId);

    /**
     * @return true si el proveedor ya tiene una compra no anulada con ese número
     *         de factura (sin distinguir mayúsculas)
     */
    @Query("""
            select count(c) > 0 from Compra c
            where c.proveedor.id = :proveedorId
              and upper(c.numeroFactura) = upper(:numeroFactura)
              and c.estadoCompra <> org.gestionComercio.enums.EstadoCompra.ANULADA
            """)
    boolean existeFactura(
            @Param("proveedorId") Long proveedorId,
            @Param("numeroFactura") String numeroFactura);

}
