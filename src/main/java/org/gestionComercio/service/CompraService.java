package org.gestionComercio.service;

import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.enums.EstadoCompra;

import java.time.LocalDate;
import java.util.List;

/**
 * Registro y consulta de compras a proveedores.
 *
 * <p>Registrar una compra guarda, en una sola transacción, la compra con sus
 * renglones, la entrada de stock de cada producto (si ya se recibió) y, si
 * se paga en efectivo, el egreso en la caja abierta del usuario. Si
 * cualquiera de las partes falla no se guarda nada.
 */
public interface CompraService {

    /**
     * Registra una compra del usuario de la sesión. Requiere COMPRA_CREAR.
     *
     * <p>De cada renglón se usan el producto, la cantidad y el precio de
     * compra; total = suma de los renglones. Sin estado, la compra queda
     * RECIBIDA y suma su stock; PENDIENTE la guarda sin mover stock hasta
     * {@link #recibir}. Con {@code actualizarPreciosCompra}, el precio de cada
     * renglón pasa a ser el precio de compra del producto.
     *
     * <p>Sólo las compras en efectivo generan un movimiento de caja (tipo
     * PAGO_PROVEEDOR), en la caja abierta del usuario.
     *
     * @return la compra guardada, con sus renglones
     * @throws org.gestionComercio.exception.ValidationException si los datos no son válidos
     *         o la factura ya se registró para ese proveedor
     * @throws org.gestionComercio.exception.BusinessException si se paga en efectivo y el
     *         usuario no tiene caja abierta o su efectivo no alcanza
     */
    CompraDto registrar(CompraDto compra);

    /**
     * Marca como recibida una compra pendiente y suma su stock. Requiere
     * COMPRA_CREAR.
     *
     * @return la compra recibida, con sus renglones
     * @throws org.gestionComercio.exception.BusinessException si la compra no está pendiente
     *         o algún producto fue dado de baja
     */
    CompraDto recibir(Long compraId);

    /**
     * Anula una compra pendiente o recibida. Requiere COMPRA_ANULAR.
     *
     * <p>En una sola transacción: si estaba recibida descuenta el stock que
     * sumó (revirtiendo sus movimientos de inventario), y si se pagó en
     * efectivo registra un ingreso de caja (tipo ANULACION_COMPRA) por el
     * total. El ingreso va a la caja de la compra si sigue abierta; si ya se
     * cerró, a la caja abierta del usuario de la sesión. La compra queda
     * ANULADA con la fecha, el usuario y el motivo, y libera su número de
     * factura.
     *
     * @param motivo obligatorio, hasta 255 caracteres
     * @return la compra anulada, con sus renglones
     * @throws org.gestionComercio.exception.BusinessException si falta el motivo, la compra
     *         ya está anulada, revertir su stock dejaría algún producto en negativo, o se
     *         pagó en efectivo y no hay caja abierta donde registrar el reintegro
     */
    CompraDto anular(Long compraId, String motivo);

    /** Una compra con sus renglones. Requiere COMPRA_VER. */
    CompraDto obtener(Long id);

    /**
     * Compras de la más reciente a la más antigua, sin sus renglones.
     * Requiere COMPRA_VER.
     *
     * @param desde       fecha mínima (incluida); null para no limitar
     * @param hasta       fecha máxima (incluida); null para no limitar
     * @param estado      estado de la compra; null para todos
     * @param proveedorId proveedor; null para todos
     * @throws org.gestionComercio.exception.BusinessException si desde es posterior a hasta
     */
    List<CompraDto> listar(LocalDate desde, LocalDate hasta, EstadoCompra estado, Long proveedorId);

    // ------------------------------------------------------------------
    // Consultas de la pantalla de compras
    //
    // Requieren COMPRA_CREAR y no los permisos de productos o proveedores:
    // quien registra compras tiene que poder elegirlos sin administrarlos.
    // ------------------------------------------------------------------

    /** Proveedores activos, ordenados por razón social. Requiere COMPRA_CREAR o COMPRA_VER (filtro del listado). */
    List<ProveedorDto> listarProveedores();

    /**
     * Productos activos por nombre, código interno o código de barras (texto
     * vacío = todos), con su precio de compra y stock actuales.
     */
    List<ProductoDto> buscarProductos(String texto);

}
