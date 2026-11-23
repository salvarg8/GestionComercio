package org.gestionComercio.arca;

import org.gestionComercio.enums.AmbienteArca;

import java.util.Optional;

/**
 * Web service de facturación electrónica de ARCA (WSFEv1, RG 4291).
 *
 * <p>Todos los métodos lanzan {@link ArcaComunicacionException} si no hubo
 * respuesta y {@link ArcaException} si ARCA devolvió un error general.
 */
public interface WsfeCliente {

    /** FEDummy: estado de los servidores de ARCA (no requiere autenticación). */
    Wsfe.EstadoServidores estado(AmbienteArca ambiente);

    /** FECompUltimoAutorizado: último número autorizado (0 si todavía no hay ninguno). */
    long ultimoAutorizado(AmbienteArca ambiente, Wsfe.Auth auth, int puntoVenta, int tipoComprobante);

    /**
     * FECAESolicitar: pide el CAE de un comprobante. Los rechazos y los errores
     * generales vienen en la respuesta, no como excepción.
     */
    Wsfe.RespuestaCae solicitarCae(AmbienteArca ambiente, Wsfe.Auth auth, Wsfe.SolicitudCae solicitud);

    /** FECompConsultar: un comprobante ya emitido; vacío si ARCA no lo tiene. */
    Optional<Wsfe.ComprobanteEmitido> consultar(AmbienteArca ambiente, Wsfe.Auth auth,
                                                int puntoVenta, int tipoComprobante, long numero);

}
