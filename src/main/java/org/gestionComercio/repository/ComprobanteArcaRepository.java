package org.gestionComercio.repository;

import org.gestionComercio.entity.ComprobanteArca;
import org.gestionComercio.enums.AmbienteArca;
import org.gestionComercio.enums.EstadoComprobanteArca;
import org.gestionComercio.enums.TipoComprobante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ComprobanteArcaRepository extends JpaRepository<ComprobanteArca, Long> {

    /** Comprobantes de la venta: la factura antes que la nota de crédito. */
    List<ComprobanteArca> findByVentaIdOrderByIdAsc(Long ventaId);

    /** Los comprobantes en esos estados, del más antiguo al más reciente, con su venta. */
    @Query("""
            select c from ComprobanteArca c
              join fetch c.venta v
              left join fetch v.cliente
            where c.estado in :estados
            order by c.id
            """)
    List<ComprobanteArca> findByEstados(@Param("estados") Collection<EstadoComprobanteArca> estados);

    /**
     * Ids de los comprobantes enviados sin respuesta en esa numeración
     * (ambiente, CUIT, punto de venta y tipo).
     */
    @Query("""
            select c.id from ComprobanteArca c
            where c.estado = org.gestionComercio.enums.EstadoComprobanteArca.ENVIADO
              and c.ambiente = :ambiente
              and c.cuitEmisor = :cuit
              and c.puntoVenta = :puntoVenta
              and c.tipoComprobante = :tipo
            order by c.numero
            """)
    List<Long> findEnviados(
            @Param("ambiente") AmbienteArca ambiente,
            @Param("cuit") String cuit,
            @Param("puntoVenta") Integer puntoVenta,
            @Param("tipo") TipoComprobante tipo);

    boolean existsByEstado(EstadoComprobanteArca estado);

}
