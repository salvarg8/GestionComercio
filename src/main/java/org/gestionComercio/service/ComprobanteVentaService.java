package org.gestionComercio.service;

/**
 * Comprobante de venta en PDF, con los datos de la empresa cargados en
 * Configuración. Los tickets y remitos no son fiscales; las facturas
 * electrónicas llevan el CAE y el código QR de ARCA una vez autorizadas.
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

}
