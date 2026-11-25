package org.gestionComercio.repository;

import jakarta.persistence.LockModeType;
import org.gestionComercio.dto.reporte.VentasPorDia;
import org.gestionComercio.dto.reporte.VentasPorFormaPago;
import org.gestionComercio.dto.reporte.VentasPorUsuario;
import org.gestionComercio.dto.venta.VentasPorCaja;
import org.gestionComercio.entity.Venta;
import org.gestionComercio.enums.EstadoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {

    /**
     * Ventas de una caja registradas después de la fecha indicada (sin
     * incluirla), sin contar las de los estados indicados. Para el control
     * del efectivo en caja.
     */
    long countByCajaIdAndFechaAfterAndEstadoVentaNotIn(
            Long cajaId, LocalDateTime fecha, Collection<EstadoVenta> estados);

    /** Una venta con su cliente, vendedor, caja y renglones (con sus productos). */
    @Query("""
            select v from Venta v
              left join fetch v.cliente
              join fetch v.usuario
              left join fetch v.caja
              left join fetch v.usuarioAnulacion
              left join fetch v.detalles d
              left join fetch d.producto
            where v.id = :id
            """)
    Optional<Venta> findConDetalles(@Param("id") Long id);

    /**
     * Una venta bloqueada para modificarla (SELECT ... FOR UPDATE) hasta que
     * termine la transacción: dos anulaciones simultáneas de la misma venta
     * se ejecutan una después de la otra, y la segunda la ve ya anulada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Venta v where v.id = :id")
    Optional<Venta> findParaModificar(@Param("id") Long id);

    /**
     * Ventas de la más reciente a la más antigua, sin sus renglones. Cada
     * filtro null no filtra; las fechas incluyen el extremo.
     */
    @Query("""
            select v from Venta v
              left join fetch v.cliente
              join fetch v.usuario
              left join fetch v.caja
              left join fetch v.usuarioAnulacion
            where (cast(:desde as LocalDateTime) is null or v.fecha >= :desde)
              and (cast(:hasta as LocalDateTime) is null or v.fecha <= :hasta)
              and (:estado is null or v.estadoVenta = :estado)
            order by v.fecha desc, v.id desc
            """)
    List<Venta> buscar(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("estado") EstadoVenta estado);

    /**
     * Lo vendido en cada caja entre las dos fechas (ambas incluidas), sin las
     * ventas anuladas ni las canceladas, ordenado por apertura de la caja.
     * Sólo aparecen las cajas con al menos una venta en el período.
     *
     * @param usuarioId responsable de las cajas; null para todas
     */
    @Query("""
            select new org.gestionComercio.dto.venta.VentasPorCaja(
                     c.id,
                     concat(u.nombre, ' ', u.apellido),
                     c.estadoCaja,
                     c.fechaApertura,
                     count(v),
                     sum(v.total))
            from Venta v
              join v.caja c
              join c.usuario u
            where v.fecha >= :desde
              and v.fecha <= :hasta
              and v.estadoVenta not in (org.gestionComercio.enums.EstadoVenta.ANULADA,
                                        org.gestionComercio.enums.EstadoVenta.CANCELADA)
              and (:usuarioId is null or u.id = :usuarioId)
            group by c.id, u.nombre, u.apellido, c.estadoCaja, c.fechaApertura
            order by c.fechaApertura, c.id
            """)
    List<VentasPorCaja> totalizarPorCaja(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("usuarioId") Long usuarioId);

    /**
     * Lo vendido sin caja (si la configuración no exige caja abierta) entre
     * las dos fechas, por vendedor, sin las ventas anuladas ni las canceladas.
     * Cada resultado tiene la caja, su estado y su apertura en null.
     *
     * @param usuarioId vendedor; null para todos
     */
    @Query("""
            select new org.gestionComercio.dto.venta.VentasPorCaja(
                     concat(u.nombre, ' ', u.apellido),
                     count(v),
                     sum(v.total))
            from Venta v
              join v.usuario u
            where v.caja is null
              and v.fecha >= :desde
              and v.fecha <= :hasta
              and v.estadoVenta not in (org.gestionComercio.enums.EstadoVenta.ANULADA,
                                        org.gestionComercio.enums.EstadoVenta.CANCELADA)
              and (:usuarioId is null or u.id = :usuarioId)
            group by u.id, u.nombre, u.apellido
            order by u.nombre, u.apellido
            """)
    List<VentasPorCaja> totalizarSinCaja(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("usuarioId") Long usuarioId);

    // ------------------------------------------------------------------
    // Reportes: sin ventas anuladas ni canceladas; cada fecha null no
    // filtra y las fechas incluyen el extremo.
    // ------------------------------------------------------------------

    /** Ventas de cada día con al menos una venta, del más antiguo al más reciente. */
    @Query("""
            select new org.gestionComercio.dto.reporte.VentasPorDia(
                     cast(v.fecha as LocalDate),
                     count(v),
                     sum(v.subtotal),
                     sum(v.descuento),
                     sum(v.total))
            from Venta v
            where (cast(:desde as LocalDateTime) is null or v.fecha >= :desde)
              and (cast(:hasta as LocalDateTime) is null or v.fecha <= :hasta)
              and v.estadoVenta not in (org.gestionComercio.enums.EstadoVenta.ANULADA,
                                        org.gestionComercio.enums.EstadoVenta.CANCELADA)
            group by cast(v.fecha as LocalDate)
            order by cast(v.fecha as LocalDate)
            """)
    List<VentasPorDia> totalizarPorDia(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    /** Ventas de cada forma de pago usada, de mayor a menor importe. */
    @Query("""
            select new org.gestionComercio.dto.reporte.VentasPorFormaPago(
                     v.formaPago,
                     count(v),
                     sum(v.total))
            from Venta v
            where (cast(:desde as LocalDateTime) is null or v.fecha >= :desde)
              and (cast(:hasta as LocalDateTime) is null or v.fecha <= :hasta)
              and v.estadoVenta not in (org.gestionComercio.enums.EstadoVenta.ANULADA,
                                        org.gestionComercio.enums.EstadoVenta.CANCELADA)
            group by v.formaPago
            order by sum(v.total) desc, v.formaPago
            """)
    List<VentasPorFormaPago> totalizarPorFormaPago(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

    /** Ventas de cada vendedor con al menos una venta, de mayor a menor importe. */
    @Query("""
            select new org.gestionComercio.dto.reporte.VentasPorUsuario(
                     u.id,
                     concat(u.nombre, ' ', u.apellido),
                     count(v),
                     sum(v.total))
            from Venta v
              join v.usuario u
            where (cast(:desde as LocalDateTime) is null or v.fecha >= :desde)
              and (cast(:hasta as LocalDateTime) is null or v.fecha <= :hasta)
              and v.estadoVenta not in (org.gestionComercio.enums.EstadoVenta.ANULADA,
                                        org.gestionComercio.enums.EstadoVenta.CANCELADA)
            group by u.id, u.nombre, u.apellido
            order by sum(v.total) desc, u.nombre, u.apellido
            """)
    List<VentasPorUsuario> totalizarPorUsuario(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta);

}
