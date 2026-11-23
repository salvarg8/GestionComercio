package org.gestionComercio.service;

import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.ResumenVentas;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.dto.venta.VentasPorCaja;
import org.gestionComercio.enums.EstadoVenta;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Registro y consulta de ventas.
 *
 * <p>Registrar una venta guarda, en una sola transacción, la venta con sus
 * renglones, la salida de stock de cada producto y, si se cobra en efectivo,
 * el ingreso en la caja abierta del vendedor. Si cualquiera de las tres
 * partes falla no se guarda nada.
 */
public interface VentaService {

    /**
     * Registra una venta del usuario de la sesión en su caja abierta. Si no
     * tiene y la configuración no exige caja abierta, la venta queda sin caja.
     * Requiere VENTA_CREAR.
     *
     * <p>De cada renglón se usan sólo el producto y la cantidad: el precio
     * unitario es el precio de venta actual del producto y queda guardado en
     * el renglón. Subtotal = suma de renglones; total = subtotal − descuento,
     * redondeado hacia abajo según la configuración (la diferencia se suma al
     * descuento). Sin cliente, la venta es a consumidor final.
     *
     * <p>Las ventas en cuenta corriente quedan PENDIENTES; el resto, PAGADAS.
     * Sólo las ventas en efectivo con caja generan un movimiento de caja (tipo VENTA).
     *
     * @return la venta guardada, con sus renglones
     * @throws org.gestionComercio.exception.BusinessException si el usuario no tiene caja abierta
     *         y la configuración la exige
     * @throws org.gestionComercio.exception.ValidationException si los datos no son válidos
     * @throws org.gestionComercio.exception.BusinessException si algún producto no tiene stock
     *         suficiente (se informan todos los faltantes juntos)
     */
    VentaDto registrar(VentaDto venta);

    /**
     * Anula una venta pagada o pendiente. Requiere VENTA_ANULAR.
     *
     * <p>En una sola transacción: devuelve al stock lo que la venta descontó
     * (revirtiendo sus movimientos de inventario), y si se cobró en efectivo
     * registra un egreso de caja (tipo ANULACION_VENTA) por el total. El
     * egreso va a la caja de la venta si sigue abierta; si ya se cerró, a la
     * caja abierta del usuario de la sesión. Una venta registrada sin caja no
     * registra egreso (su efectivo no entró a ninguna). La venta queda ANULADA con la
     * fecha, el usuario y el motivo, y sigue apareciendo en el listado.
     *
     * @param motivo obligatorio, hasta 255 caracteres
     * @return la venta anulada, con sus renglones
     * @throws org.gestionComercio.exception.BusinessException si falta el motivo,
     *         la venta ya está anulada, o es en efectivo y no hay caja abierta donde
     *         registrar el egreso o el efectivo de esa caja no alcanza
     */
    VentaDto anular(Long ventaId, String motivo);

    /** Una venta con sus renglones. Requiere VENTA_VER. */
    VentaDto obtener(Long id);

    /**
     * Ventas de todos los usuarios, de la más reciente a la más antigua, sin
     * sus renglones. Requiere VENTA_VER.
     *
     * @param desde  fecha mínima (incluida); null para no limitar
     * @param hasta  fecha máxima (incluida); null para no limitar
     * @param estado estado de la venta; null para todos
     * @throws org.gestionComercio.exception.BusinessException si desde es posterior a hasta
     */
    List<VentaDto> listar(LocalDate desde, LocalDate hasta, EstadoVenta estado);

    /**
     * Lo vendido hoy en cada caja, de todos los usuarios, sin contar las
     * ventas anuladas ni las canceladas. Aparecen todas las cajas abiertas
     * (con cero si hoy no vendieron) y las ya cerradas que vendieron hoy,
     * ordenadas por apertura; al final, lo vendido sin caja por cada vendedor
     * (con la caja en null). Requiere VENTA_VER.
     */
    List<VentasPorCaja> ventasDelDiaPorCaja();

    /**
     * Cantidad e importe de lo vendido hoy en las cajas del usuario de la
     * sesión (y sin caja), sin contar las ventas anuladas ni las canceladas. Requiere
     * VENTA_CREAR: es lo que un vendedor puede ver de sus propias ventas.
     */
    ResumenVentas ventasDelDiaPropias();

    // ------------------------------------------------------------------
    // Consultas del punto de venta
    //
    // Requieren VENTA_CREAR y no los permisos de productos, clientes o caja:
    // un vendedor tiene que poder buscar lo que vende sin administrarlo.
    // ------------------------------------------------------------------

    /** @return true si el usuario de la sesión tiene una caja abierta donde registrar ventas. */
    boolean tieneCajaAbierta();

    /**
     * Productos activos por nombre, código interno o código de barras (texto
     * vacío = todos), con su precio de venta y stock actuales.
     */
    List<ProductoDto> buscarProductos(String texto);

    /**
     * Producto activo cuyo código de barras o código interno es exactamente el
     * indicado (el código interno sin distinguir mayúsculas). Pensado para el
     * lector de código de barras.
     */
    Optional<ProductoDto> buscarProductoPorCodigo(String codigo);

    /** Clientes activos, ordenados por apellido y nombre. */
    List<ClienteDto> listarClientes();

    /**
     * Controla el stock actual de los renglones sin registrar nada (los
     * renglones del mismo producto se suman). Los productos que no manejan
     * stock o permiten stock negativo nunca faltan.
     *
     * @return un mensaje por producto sin stock suficiente; vacío si alcanza para todos
     */
    List<String> verificarStock(List<DetalleVentaDto> detalles);

}
