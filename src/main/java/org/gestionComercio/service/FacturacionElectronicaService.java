package org.gestionComercio.service;

import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.enums.TipoComprobante;

import java.util.List;

/**
 * Autorización de los comprobantes electrónicos en ARCA (CAE).
 *
 * <p>Registrar una venta con factura (o anular una venta facturada) deja el
 * comprobante PENDIENTE; después, fuera de esa transacción, se llama a
 * {@link #autorizar(Long)}. Si ARCA no responde, la venta queda registrada
 * y el comprobante pendiente para reintentarlo más tarde.
 *
 * <p>Garantías:
 * <ul>
 *   <li>El número es siempre el último autorizado en ARCA + 1, consultado
 *       con la numeración bloqueada: dos cajas no piden el mismo número.</li>
 *   <li>Antes de enviar se guarda el número asignado (ENVIADO). Si se corta
 *       la conexión, el próximo intento lo consulta en ARCA
 *       (FECompConsultar) antes de volver a enviarlo: nunca se duplica.</li>
 *   <li>El ticket de acceso del WSAA se reutiliza hasta que vence.</li>
 * </ul>
 */
public interface FacturacionElectronicaService {

    /** @return true si la facturación electrónica está habilitada. Requiere VENTA_CREAR, VENTA_VER o FACTURACION_REINTENTAR. */
    boolean estaHabilitada();

    /**
     * La factura que corresponde emitirle al cliente según la condición frente
     * al IVA del comercio y la suya. Requiere VENTA_CREAR.
     *
     * @param clienteId null para consumidor final
     * @throws org.gestionComercio.exception.BusinessException si la facturación no está habilitada
     */
    TipoComprobante tipoFactura(Long clienteId);

    /**
     * Pide el CAE de los comprobantes de la venta que falten autorizar (la
     * factura y, si se anuló, la nota de crédito), incluidos los rechazados
     * (después de corregir el motivo). Requiere VENTA_CREAR, VENTA_ANULAR (para pedirlo al
     * registrar o anular la venta) o FACTURACION_REINTENTAR.
     *
     * <p>No debe llamarse dentro de una transacción.
     *
     * @return los comprobantes de la venta con su estado: AUTORIZADO; RECHAZADO con
     *         el motivo en las observaciones; o ENVIADO si ARCA no respondió al
     *         pedir el CAE (se confirma en el próximo intento)
     * @throws org.gestionComercio.exception.BusinessException si no se pudo llegar a
     *         enviar el pedido (configuración, conexión, ticket de acceso): en ese
     *         caso no cambió nada
     */
    List<ComprobanteArcaDto> autorizar(Long ventaId);

    /**
     * Reintenta todos los comprobantes pendientes o enviados sin respuesta (no
     * los rechazados), del más antiguo al más reciente. Se detiene si no hay
     * conexión con ARCA. Requiere FACTURACION_REINTENTAR.
     *
     * @return los comprobantes procesados con su estado
     */
    List<ComprobanteArcaDto> autorizarPendientes();

    /** Comprobantes pendientes, enviados sin respuesta o rechazados, del más antiguo al más reciente. Requiere FACTURACION_REINTENTAR o VENTA_VER. */
    List<ComprobanteArcaDto> listarSinAutorizar();

}
