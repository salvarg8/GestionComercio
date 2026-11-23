package org.gestionComercio.service;

import org.gestionComercio.dto.configuracion.ConfiguracionDto;

/**
 * Comprobante de venta en PDF, con los datos de la empresa cargados en
 * Configuración y el formato de la configuración del sistema (formato del
 * ticket, logo y texto de pie). Los tickets y remitos no son fiscales; las
 * facturas electrónicas llevan el CAE y el código QR de ARCA una vez autorizadas.
 */
public interface ComprobanteVentaService {

    /**
     * Genera el comprobante de la venta, o lo vuelve a generar para
     * reimprimirlo. Si la venta está anulada, sale marcado como ANULADA.
     *
     * <p>Requiere VENTA_VER (listado de ventas) o VENTA_CREAR (el vendedor
     * imprime el comprobante al terminar la venta).
     *
     * @return el PDF
     * @throws org.gestionComercio.exception.EntityNotFoundException si no existe la venta
     *         o no hay datos de la empresa cargados
     */
    byte[] generarPdf(Long ventaId);

    /**
     * Ticket de ejemplo para probar la impresora, con los datos de la empresa
     * y el formato indicado (puede no estar guardado todavía).
     * Requiere CONFIGURACION_VER.
     *
     * @param formato formato a probar; null = el de la configuración guardada
     * @return el PDF
     */
    byte[] generarPrueba(ConfiguracionDto formato);

}
