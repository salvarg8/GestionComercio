package org.gestionComercio.repository;

import org.gestionComercio.dto.reporte.ProductoVendido;
import org.gestionComercio.entity.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {

    /**
     * Lo vendido de cada producto, de mayor a menor cantidad, sin las ventas
     * anuladas ni las canceladas. Cada fecha null no filtra; las fechas
     * incluyen el extremo.
     */
    @Query("""
            select new org.gestionComercio.dto.reporte.ProductoVendido(
                     p.id,
                     p.codigo,
                     p.nombre,
                     p.unidadMedida,
                     sum(d.cantidad),
                     sum(d.subtotal))
            from DetalleVenta d
              join d.venta v
              join d.producto p
            where (cast(:desde as LocalDateTime) is null or v.fecha >= :desde)
              and (cast(:hasta as LocalDateTime) is null or v.fecha <= :hasta)
              and v.estadoVenta not in (org.gestionComercio.enums.EstadoVenta.ANULADA,
                                        org.gestionComercio.enums.EstadoVenta.CANCELADA)
            group by p.id, p.codigo, p.nombre, p.unidadMedida
            order by sum(d.cantidad) desc, sum(d.subtotal) desc, p.nombre
            """)
    List<ProductoVendido> totalizarPorProducto(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);
}
