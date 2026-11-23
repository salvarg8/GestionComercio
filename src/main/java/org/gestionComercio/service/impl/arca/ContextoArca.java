package org.gestionComercio.service.impl.arca;

import org.gestionComercio.arca.CredencialArca;
import org.gestionComercio.enums.AmbienteArca;
import org.gestionComercio.enums.CondicionIVA;

/**
 * Lo necesario para hablar con ARCA en nombre del comercio: ambiente, CUIT,
 * punto de venta y certificado.
 *
 * @param credencial null si sólo se leyó la configuración (sin abrir el certificado)
 */
public record ContextoArca(
        AmbienteArca ambiente,
        String cuit,
        int puntoVenta,
        CondicionIVA condicionIva,
        CredencialArca credencial
) {

    public long cuitNumero() {
        return Long.parseLong(cuit);
    }

    @Override
    public String toString() {
        return "ContextoArca[" + ambiente + ", CUIT " + cuit + ", PV " + puntoVenta + "]";
    }

}
