package org.gestionComercio.service;

import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
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
     * Registra una venta del usuario de la sesión en su caja abierta.
     * Requiere VENTA_CREAR.
     *
     * <p>De cada renglón se usan sólo el producto y la cantidad: el precio
     * unitario es el precio de venta actual del producto y queda guardado en
     * el renglón. Subtotal = suma de renglones; total = subtotal − descuento.
     * Sin cliente, la venta es a consumidor final.
     *
     * <p>Las ventas en cuenta corriente quedan PENDIENTES; el resto, PAGADAS.
     * Sólo las ventas en efectivo generan un movimiento de caja (tipo VENTA).
     *
     * @return la venta guardada, con sus renglones
     * @throws org.gestionComercio.exception.BusinessException si el usuario no tiene caja abierta
     * @throws org.gestionComercio.exception.ValidationException si los datos no son válidos
     * @throws org.gestionComercio.exception.BusinessException si algún producto no tiene stock
     *         suficiente (se informan todos los faltantes juntos)
     */
    VentaDto registrar(VentaDto venta);

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
