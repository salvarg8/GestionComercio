package org.gestionComercio.service;

import org.gestionComercio.dto.inventario.LineaInventario;
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.enums.OrigenMovimientoInventario;

import java.util.List;

/**
 * Movimientos de stock que generan los comprobantes (ventas y compras). Lo usan
 * los servicios de ventas y compras, nunca una pantalla directamente.
 *
 * <p>No exige permisos de inventario: el servicio del comprobante ya controló
 * los suyos (por ejemplo, VENTA_CREAR), así un vendedor no necesita además
 * permisos de inventario. Sólo exige una sesión iniciada.
 *
 * <p>Se une a la transacción del comprobante: si falla el stock no se guarda
 * el comprobante, y viceversa. Un conflicto de concurrencia
 * ({@link org.gestionComercio.exception.ConcurrencyException}) deja esa
 * transacción marcada para revertirse.
 *
 * <p>Cada comprobante registra sus movimientos en una sola llamada (un
 * movimiento por producto; los renglones repetidos se suman) y se revierte
 * también de una vez. Los productos que no manejan stock se ignoran.
 */
public interface InventarioComprobanteService {

    /**
     * Descuenta el stock de los renglones (por ejemplo, al confirmar una venta).
     * Valida todos los renglones antes de guardar e informa juntos todos los
     * productos sin stock suficiente.
     *
     * @param origen        tipo de comprobante (no puede ser MANUAL)
     * @param comprobanteId ID del comprobante
     * @return los movimientos registrados (vacío si ningún producto maneja stock)
     * @throws org.gestionComercio.exception.BusinessException si el comprobante ya
     *         registró sus movimientos, o algún renglón no es válido o no tiene stock
     */
    List<MovimientoInventarioDto> registrarSalidas(
            OrigenMovimientoInventario origen, Long comprobanteId, List<LineaInventario> lineas, String motivo);

    /**
     * Suma al stock los renglones (por ejemplo, al recibir una compra).
     *
     * @see #registrarSalidas
     */
    List<MovimientoInventarioDto> registrarEntradas(
            OrigenMovimientoInventario origen, Long comprobanteId, List<LineaInventario> lineas, String motivo);

    /**
     * Deshace todos los movimientos del comprobante (por ejemplo, al anularlo)
     * registrando uno opuesto por cada uno. Funciona aunque el producto se haya
     * dado de baja después.
     *
     * @return los movimientos de reversión (vacío si el comprobante no movió stock)
     * @throws org.gestionComercio.exception.BusinessException si ya fue revertido, o
     *         si revertir una entrada dejaría stock negativo
     */
    List<MovimientoInventarioDto> revertir(OrigenMovimientoInventario origen, Long comprobanteId, String motivo);

}
