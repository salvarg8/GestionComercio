package org.gestionComercio.service;

import org.gestionComercio.dto.arca.ConfiguracionArcaDto;
import org.gestionComercio.dto.arca.PruebaConexionArca;

/**
 * Configuración de la facturación electrónica del comercio: ambiente, punto
 * de venta y certificado (modelo de certificado propio: la clave privada se
 * genera en el sistema y nunca sale del comercio).
 *
 * <p>Pasos para un comercio nuevo:
 * <ol>
 *   <li>{@link #generarPedidoCertificado()} y subir el CSR a ARCA
 *       ("Administración de Certificados Digitales", o WSASS en homologación).</li>
 *   <li>{@link #importarCertificado(byte[])} con el .crt que devuelve ARCA.</li>
 *   <li>Asociar el certificado al servicio "Facturación Electrónica" en el
 *       Administrador de Relaciones de Clave Fiscal.</li>
 *   <li>{@link #guardar} con el punto de venta web services y
 *       {@link #probarConexion()}; por último, habilitarla.</li>
 * </ol>
 */
public interface ConfiguracionArcaService {

    /** Nombre del "computador fiscal" en el pedido de certificado: el alias con que aparece en ARCA. */
    String ALIAS_COMPUTADOR = "GestionComercio";

    /** Requiere FACTURACION_CONFIGURAR. Sin configuración guardada, devuelve la de por defecto (homologación, deshabilitada). */
    ConfiguracionArcaDto obtener();

    /**
     * Guarda el ambiente, el punto de venta y si está habilitada. Requiere FACTURACION_CONFIGURAR.
     *
     * @throws org.gestionComercio.exception.BusinessException si se habilita sin punto de
     *         venta, sin certificado vigente o sin la fecha de inicio de actividades de la
     *         empresa; o si se cambia el ambiente o el punto de venta con comprobantes
     *         enviados sin confirmar
     */
    ConfiguracionArcaDto guardar(ConfiguracionArcaDto configuracion);

    /**
     * Guarda en esta PC la clave con que se cifra el certificado (en el perfil
     * del usuario de Windows, nunca en la base). Tiene que ser la misma en
     * todas las PC: si ya hay un certificado o un pedido guardado, se verifica
     * que la clave lo abra. Requiere FACTURACION_CONFIGURAR.
     *
     * @throws org.gestionComercio.exception.BusinessException si es muy corta, si no abre el
     *         certificado guardado o si esta PC usa la variable de entorno ARCA_CLAVE_CERTIFICADO
     */
    ConfiguracionArcaDto guardarClaveEnEstaPc(char[] clave);

    /**
     * Cambia la clave con que se cifra el certificado: lo abre con la clave de
     * esta PC, lo vuelve a cifrar con la nueva (también el pedido pendiente, si
     * hay) y guarda la nueva en esta PC. El certificado de ARCA no cambia. Las
     * demás PC tienen que cargar la clave nueva. Requiere FACTURACION_CONFIGURAR.
     *
     * @throws org.gestionComercio.exception.BusinessException si esta PC no tiene la clave
     *         actual (o no abre el certificado), si usa la variable de entorno, o si la
     *         nueva es muy corta o igual a la actual
     */
    ConfiguracionArcaDto cambiarClave(char[] nueva);

    /**
     * Descarta el certificado y el pedido guardados y deshabilita la
     * facturación. Es lo que queda cuando se perdió la clave: después hay que
     * cargar una clave nueva y generar otro certificado. Requiere FACTURACION_CONFIGURAR.
     */
    ConfiguracionArcaDto descartarCertificado();

    /**
     * Genera una clave privada nueva y su pedido de certificado (CSR) con el
     * CUIT y la razón social de la empresa. El certificado vigente sigue en uso
     * hasta que se importe el nuevo. Requiere FACTURACION_CONFIGURAR.
     *
     * @return el CSR en PEM, para guardarlo como .csr y subirlo a ARCA
     */
    String generarPedidoCertificado();

    /**
     * Importa el certificado (.crt, PEM o DER) que ARCA emitió para el último
     * pedido generado y lo pone en uso. Requiere FACTURACION_CONFIGURAR.
     *
     * @throws org.gestionComercio.exception.BusinessException si no hay pedido pendiente,
     *         el certificado no corresponde a ese pedido, es de otro CUIT o está vencido
     */
    ConfiguracionArcaDto importarCertificado(byte[] certificado);

    /**
     * Importa un certificado con su clave privada generados fuera del
     * sistema (.p12 / .pfx). Requiere FACTURACION_CONFIGURAR.
     *
     * @param contrasenia la del archivo; se vuelve a cifrar con la clave del sistema
     */
    ConfiguracionArcaDto importarPkcs12(byte[] archivo, char[] contrasenia);

    /**
     * Verifica que ARCA esté en línea, que el certificado sirva para obtener
     * un ticket de acceso y que el punto de venta esté habilitado para web
     * services (consultando el último comprobante autorizado). Funciona aunque
     * la facturación todavía no esté habilitada. Requiere FACTURACION_CONFIGURAR.
     *
     * @return el resultado de cada paso; un error de ARCA o de conexión no se lanza:
     *         queda como el resultado del paso, con el motivo y cómo corregirlo
     */
    PruebaConexionArca probarConexion();

}
