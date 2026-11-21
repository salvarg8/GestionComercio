package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.inventario.FiltroMovimientos;
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.entity.MovimientoInventario;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.MotivoAjusteInventario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.mapper.MovimientoInventarioMapper;
import org.gestionComercio.repository.MovimientoInventarioRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.service.InventarioService;
import org.gestionComercio.util.CantidadUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Cualquier excepción revierte la transacción completa: ni el movimiento ni el
 * nuevo stock quedan guardados.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class InventarioServiceImpl implements InventarioService {

    private final RegistradorMovimientosInventario registrador;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final MovimientoInventarioMapper movimientoInventarioMapper;
    private final SesionUsuario sesionUsuario;

    @Override
    public MovimientoInventarioDto registrarEntrada(Long productoId, BigDecimal cantidad, String motivo) {

        sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_REGISTRAR_ENTRADA);

        requerirPositiva(cantidad, "entrada");

        return registrar(productoId, (producto, usuario) -> MovimientoInventario.nuevo()
                .producto(producto)
                .tipo(TipoMovimientoInventario.ENTRADA)
                .cantidad(cantidad)
                .motivo(motivo)
                .usuario(usuario)
                .registrar());
    }

    @Override
    public MovimientoInventarioDto registrarSalida(Long productoId, BigDecimal cantidad, String motivo) {

        sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_REGISTRAR_SALIDA);

        requerirPositiva(cantidad, "salida");

        return registrar(productoId, (producto, usuario) -> MovimientoInventario.nuevo()
                .producto(producto)
                .tipo(TipoMovimientoInventario.SALIDA)
                .cantidad(cantidad.negate())
                .motivo(motivo)
                .usuario(usuario)
                .registrar());
    }

    @Override
    public MovimientoInventarioDto registrarAjuste(
            Long productoId, BigDecimal variacion, MotivoAjusteInventario causa, String observacion) {

        sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_AJUSTAR);

        if (variacion == null || variacion.signum() == 0) {
            throw new BusinessException("La cantidad del ajuste no puede ser cero.");
        }

        requerirMotivo(causa, observacion);

        return registrar(productoId, (producto, usuario) -> ajuste(producto, variacion, causa, observacion, usuario));
    }

    @Override
    public MovimientoInventarioDto ajustarStock(
            Long productoId, BigDecimal stockContado, MotivoAjusteInventario causa, String observacion) {

        sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_AJUSTAR);

        if (stockContado == null) {
            throw new BusinessException("Debe indicar el stock contado.");
        }

        if (stockContado.signum() < 0) {
            throw new BusinessException("El stock contado no puede ser negativo.");
        }

        requerirMotivo(causa, observacion);

        return registrar(productoId, (producto, usuario) -> {

            BigDecimal variacion = stockContado.subtract(CantidadUtils.normalizar(producto.getStock()));

            if (variacion.signum() == 0) {
                throw new BusinessException("El stock contado coincide con el del sistema ("
                        + CantidadUtils.formatear(stockContado) + "). No hay nada que ajustar.");
            }

            return ajuste(producto, variacion, causa, observacion, usuario);
        });
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovimientoInventarioDto> historial(Long productoId) {

        sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_VER);

        if (productoId == null) {
            throw new BusinessException("El producto es obligatorio.");
        }

        return movimientoInventarioMapper.toDtoList(
                movimientoInventarioRepository.findHistorialPorProducto(productoId));
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovimientoInventarioDto> buscarMovimientos(LocalDate desde, LocalDate hasta, Long productoId) {

        if (desde == null || hasta == null) {
            sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_VER);
            throw new BusinessException("Debe indicar las fechas desde y hasta.");
        }

        return consultarMovimientos(new FiltroMovimientos(productoId, null, desde, hasta));
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovimientoInventarioDto> consultarMovimientos(FiltroMovimientos filtro) {

        sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_VER);

        FiltroMovimientos criterios = filtro == null ? FiltroMovimientos.todos() : filtro;
        LocalDate desde = criterios.desde();
        LocalDate hasta = criterios.hasta();

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }

        return movimientoInventarioMapper.toDtoList(movimientoInventarioRepository.consultar(
                criterios.productoId(),
                criterios.tipo(),
                desde == null ? null : desde.atStartOfDay(),
                DateUtils.finDelDia(hasta)));
    }

    /**
     * Las operaciones manuales sólo se hacen sobre productos activos que manejan stock.
     */
    private MovimientoInventarioDto registrar(Long productoId, CreadorMovimiento creador) {

        if (productoId == null) {
            throw new BusinessException("El producto es obligatorio.");
        }

        Usuario usuario = registrador.usuarioResponsable();
        Producto producto = registrador.cargarProductos(List.of(productoId), true).get(productoId);

        if (!Boolean.TRUE.equals(producto.getManejaStock())) {
            throw new BusinessException("El producto \"" + producto.getNombre() + "\" no maneja stock.");
        }

        List<MovimientoInventario> guardados = registrador.registrar(
                List.of(() -> creador.crear(producto, usuario)));

        return movimientoInventarioMapper.toDto(guardados.get(0));
    }

    /** El motivo del movimiento es la observación, o la causa si no se escribió ninguna. */
    private MovimientoInventario ajuste(
            Producto producto,
            BigDecimal variacion,
            MotivoAjusteInventario causa,
            String observacion,
            Usuario usuario) {

        String motivo = observacion == null || observacion.isBlank() ? causa.getDescripcion() : observacion;

        return MovimientoInventario.nuevo()
                .producto(producto)
                .tipo(TipoMovimientoInventario.AJUSTE)
                .cantidad(variacion)
                .motivo(motivo)
                .motivoAjuste(causa)
                .usuario(usuario)
                .registrar();
    }

    private void requerirPositiva(BigDecimal cantidad, String operacion) {

        if (cantidad == null || cantidad.signum() <= 0) {
            throw new BusinessException("La cantidad de la " + operacion + " debe ser mayor que cero.");
        }
    }

    /**
     * Un ajuste siempre tiene motivo: la causa es obligatoria y, si es
     * {@code OTRO}, también el detalle, porque la causa sola no explica nada.
     */
    private void requerirMotivo(MotivoAjusteInventario causa, String observacion) {

        if (causa == null) {
            throw new BusinessException("Debe indicar la causa del ajuste.");
        }

        if (causa.requiereDetalle() && (observacion == null || observacion.isBlank())) {
            throw new BusinessException("Debe detallar el motivo del ajuste cuando la causa es \""
                    + causa.getDescripcion() + "\".");
        }

        if (observacion != null && observacion.trim().length() > MovimientoInventario.MOTIVO_MAX) {
            throw new BusinessException("El detalle del ajuste no puede superar los "
                    + MovimientoInventario.MOTIVO_MAX + " caracteres.");
        }
    }

    /** Crea el movimiento a partir del producto ya cargado y validado. */
    @FunctionalInterface
    private interface CreadorMovimiento {
        MovimientoInventario crear(Producto producto, Usuario usuario);
    }

}
