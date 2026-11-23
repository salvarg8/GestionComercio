package org.gestionComercio.arca;

import org.gestionComercio.enums.AmbienteArca;

/** Web service de autenticación y autorización de ARCA (WSAA). */
public interface WsaaCliente {

    /**
     * Pide un ticket de acceso (loginCms).
     *
     * @param traFirmado TRA firmado en CMS y en base64 ({@link AutenticacionWsaa#firmar})
     * @throws ArcaComunicacionException si no hubo respuesta
     * @throws ArcaException             si ARCA rechazó el pedido
     */
    TicketAcceso login(AmbienteArca ambiente, String traFirmado);

}
