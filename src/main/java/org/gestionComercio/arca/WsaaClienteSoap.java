package org.gestionComercio.arca;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.enums.AmbienteArca;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

@Component
@RequiredArgsConstructor
public class WsaaClienteSoap implements WsaaCliente {

    private static final String NAMESPACE = "xmlns:wsaa=\"http://wsaa.view.sua.dvadac.desein.afip.gov\"";

    private final SoapCliente soap;
    private final ArcaProperties properties;

    @Override
    public TicketAcceso login(AmbienteArca ambiente, String traFirmado) {

        Document respuesta = soap.enviar(properties.urlWsaa(ambiente), "", NAMESPACE,
                "<wsaa:loginCms><wsaa:in0>" + traFirmado + "</wsaa:in0></wsaa:loginCms>");

        // loginCmsReturn trae el loginTicketResponse como texto (XML escapado).
        return AutenticacionWsaa.leerRespuesta(Xml.textoObligatorio(respuesta, "loginCmsReturn"));
    }

}
