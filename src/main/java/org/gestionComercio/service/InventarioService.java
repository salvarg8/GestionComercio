package org.gestionComercio.service;

import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.enums.MotivoAjusteInventario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Operaciones de inventario que se hacen a mano desde la pantalla de
 * inventario. Cada una exige su permiso del módulo Inventario
 * ({@code PermisoCodigo.INVENTARIO_*}) y registra movimientos con origen
 * {@code MANUAL}.
 *
 * <p>Las ventas y compras no usan este servicio sino
 * {@link InventarioComprobanteService}, que no exige permisos de inventario
 * porque el comprobante ya controló los suyos.
 *
 * <p>Cada operación registra un movimiento y actualiza el stock del producto
 * en la misma transacción: si falla una de las dos cosas, no se aplica
 * ninguna. Política de stock negativo: ningún movimiento puede dejar el stock
 * por debajo de cero, salvo que el producto lo permita
 * ({@code permiteStockNegativo}). Las cantidades sólo admiten decimales si la
 * unidad del producto es fraccionable (kilo, litro, metro).
 *
 * @see org.gestionComercio.exception.ConcurrencyException si otra operación
 *      modificó el producto al mismo tiempo
 */
public interface InventarioService {

    /**
     * Ingreso de mercadería. Requiere INVENTARIO_REGISTRAR_ENTRADA.
     *
     * @param cantidad unidades que ingresan (mayor que cero)
     */
    MovimientoInventarioDto registrarEntrada(Long productoId, BigDecimal cantidad, String motivo);

    /**
     * Egreso de mercadería. Verifica que haya stock suficiente. Requiere
     * INVENTARIO_REGISTRAR_SALIDA.
     *
     * @param cantidad unidades que egresan (mayor que cero)
     */
    MovimientoInventarioDto registrarSalida(Long productoId, BigDecimal cantidad, String motivo);

    /**
     * Corrección de stock por una variación conocida (por ejemplo, 3 unidades
     * rotas). Requiere INVENTARIO_AJUSTAR.
     *
     * @param variacion   unidades a sumar (positiva) o a restar (negativa); distinta de cero
     * @param causa       causa del ajuste, para reportes
     * @param observacion detalle opcional; si está vacío se usa la descripción de la causa
     */
    MovimientoInventarioDto registrarAjuste(
            Long productoId, BigDecimal variacion, MotivoAjusteInventario causa, String observacion);

    /**
     * Ajuste por recuento: el usuario indica cuánto stock contó y se registra
     * la diferencia con el stock del sistema. Requiere INVENTARIO_AJUSTAR.
     *
     * @param stockContado stock real contado (cero o más)
     * @throws org.gestionComercio.exception.BusinessException si coincide con el stock actual
     */
    MovimientoInventarioDto ajustarStock(
            Long productoId, BigDecimal stockContado, MotivoAjusteInventario causa, String observacion);

    /** Movimientos de un producto, del más reciente al más antiguo. Requiere INVENTARIO_VER. */
    List<MovimientoInventarioDto> historial(Long productoId);

    /**
     * Movimientos entre dos fechas (ambas incluidas), del más reciente al más
     * antiguo. Requiere INVENTARIO_VER.
     *
     * @param productoId null para incluir todos los productos
     */
    List<MovimientoInventarioDto> buscarMovimientos(LocalDate desde, LocalDate hasta, Long productoId);

}
