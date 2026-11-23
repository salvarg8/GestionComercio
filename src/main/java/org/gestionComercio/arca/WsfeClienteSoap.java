package org.gestionComercio.arca;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.enums.AmbienteArca;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cliente SOAP del WSFEv1 armado a mano: son pocos métodos y así no hace falta
 * generar clases desde el WSDL ni descargarlo al compilar. Sigue el manual
 * del desarrollador de WSFEv1 (RG 4291, v4.x).
 */
@Component
@RequiredArgsConstructor
public class WsfeClienteSoap implements WsfeCliente {

    static final String NS = "http://ar.gov.afip.dif.FEV1/";
    private static final String NAMESPACE = "xmlns:ar=\"" + NS + "\"";

    static final DateTimeFormatter FECHA = DateTimeFormatter.BASIC_ISO_DATE;

    private final SoapCliente soap;
    private final ArcaProperties properties;

    @Override
    public Wsfe.EstadoServidores estado(AmbienteArca ambiente) {

        Document r = llamar(ambiente, "FEDummy", "<ar:FEDummy/>");

        return new Wsfe.EstadoServidores(
                Xml.texto(r, "AppServer").orElse(""),
                Xml.texto(r, "DbServer").orElse(""),
                Xml.texto(r, "AuthServer").orElse(""));
    }

    @Override
    public long ultimoAutorizado(AmbienteArca ambiente, Wsfe.Auth auth, int puntoVenta, int tipoComprobante) {

        Document r = llamar(ambiente, "FECompUltimoAutorizado", "<ar:FECompUltimoAutorizado>" + auth(auth)
                + "<ar:PtoVta>" + puntoVenta + "</ar:PtoVta>"
                + "<ar:CbteTipo>" + tipoComprobante + "</ar:CbteTipo>"
                + "</ar:FECompUltimoAutorizado>");

        List<Wsfe.Mensaje> errores = mensajes(r, "Errors", "Err");

        if (!errores.isEmpty()) {
            throw new ArcaException("ARCA no informó el último comprobante autorizado del punto de venta "
                    + puntoVenta + ": " + unir(errores));
        }

        return Long.parseLong(Xml.textoObligatorio(r, "CbteNro"));
    }

    @Override
    public Wsfe.RespuestaCae solicitarCae(AmbienteArca ambiente, Wsfe.Auth auth, Wsfe.SolicitudCae s) {

        Document r = llamar(ambiente, "FECAESolicitar", "<ar:FECAESolicitar>" + auth(auth)
                + "<ar:FeCAEReq>"
                + "<ar:FeCabReq>"
                + "<ar:CantReg>1</ar:CantReg>"
                + "<ar:PtoVta>" + s.puntoVenta() + "</ar:PtoVta>"
                + "<ar:CbteTipo>" + s.tipoComprobante() + "</ar:CbteTipo>"
                + "</ar:FeCabReq>"
                + "<ar:FeDetReq><ar:FECAEDetRequest>" + detalle(s) + "</ar:FECAEDetRequest></ar:FeDetReq>"
                + "</ar:FeCAEReq>"
                + "</ar:FECAESolicitar>");

        List<Wsfe.Mensaje> errores = mensajes(r, "Errors", "Err");
        Optional<Element> det = Xml.elemento(r, "FECAEDetResponse");

        if (det.isEmpty()) {
            return new Wsfe.RespuestaCae(null, null, null, List.of(), errores);
        }

        return new Wsfe.RespuestaCae(
                Xml.texto(det.get(), "Resultado").orElse(null),
                Xml.texto(det.get(), "CAE").orElse(null),
                Xml.texto(det.get(), "CAEFchVto").map(WsfeClienteSoap::fecha).orElse(null),
                mensajes(det.get(), "Observaciones", "Obs"),
                errores);
    }

    @Override
    public Optional<Wsfe.ComprobanteEmitido> consultar(AmbienteArca ambiente, Wsfe.Auth auth,
                                                       int puntoVenta, int tipoComprobante, long numero) {

        Document r = llamar(ambiente, "FECompConsultar", "<ar:FECompConsultar>" + auth(auth)
                + "<ar:FeCompConsReq>"
                + "<ar:CbteTipo>" + tipoComprobante + "</ar:CbteTipo>"
                + "<ar:CbteNro>" + numero + "</ar:CbteNro>"
                + "<ar:PtoVta>" + puntoVenta + "</ar:PtoVta>"
                + "</ar:FeCompConsReq>"
                + "</ar:FECompConsultar>");

        List<Wsfe.Mensaje> errores = mensajes(r, "Errors", "Err");

        if (errores.stream().anyMatch(e -> e.codigo() == Wsfe.ERROR_SIN_RESULTADOS)) {
            return Optional.empty();
        }

        if (!errores.isEmpty()) {
            throw new ArcaException("No se pudo consultar el comprobante en ARCA: " + unir(errores));
        }

        Optional<Element> resultado = Xml.elemento(r, "ResultGet");

        if (resultado.isEmpty()) {
            return Optional.empty();
        }

        Element g = resultado.get();

        return Optional.of(new Wsfe.ComprobanteEmitido(
                Integer.parseInt(Xml.texto(g, "PtoVta").orElse(String.valueOf(puntoVenta))),
                Integer.parseInt(Xml.texto(g, "CbteTipo").orElse(String.valueOf(tipoComprobante))),
                Long.parseLong(Xml.texto(g, "CbteDesde").orElse(String.valueOf(numero))),
                Xml.texto(g, "CbteFch").map(WsfeClienteSoap::fecha).orElse(null),
                Integer.parseInt(Xml.texto(g, "DocTipo").orElse("0")),
                Long.parseLong(Xml.texto(g, "DocNro").orElse("0")),
                new BigDecimal(Xml.texto(g, "ImpTotal").orElse("0")),
                Xml.texto(g, "Resultado").orElse(null),
                Xml.texto(g, "CodAutorizacion").orElse(null),
                Xml.texto(g, "FchVto").map(WsfeClienteSoap::fecha).orElse(null)));
    }

    // ------------------------------------------------------------------
    // Armado del pedido
    // ------------------------------------------------------------------

    /** Detalle del comprobante en el orden de FECAEDetRequest del WSDL. */
    static String detalle(Wsfe.SolicitudCae s) {

        StringBuilder x = new StringBuilder()
                .append(campo("Concepto", Wsfe.CONCEPTO_PRODUCTOS))
                .append(campo("DocTipo", s.docTipo()))
                .append(campo("DocNro", s.docNro()))
                .append(campo("CbteDesde", s.numero()))
                .append(campo("CbteHasta", s.numero()))
                .append(campo("CbteFch", FECHA.format(s.fecha())))
                .append(campo("ImpTotal", importe(s.importeTotal())))
                .append(campo("ImpTotConc", importe(s.importeNoGravado())))
                .append(campo("ImpNeto", importe(s.importeNeto())))
                .append(campo("ImpOpEx", importe(s.importeExento())))
                .append(campo("ImpTrib", importe(s.importeTributos())))
                .append(campo("ImpIVA", importe(s.importeIva())))
                .append(campo("MonId", Wsfe.MONEDA_PESOS))
                .append(campo("MonCotiz", 1))
                .append(campo("CondicionIVAReceptorId", s.condicionIvaReceptor()));

        if (s.asociado() != null) {
            Wsfe.ComprobanteAsociado a = s.asociado();
            x.append("<ar:CbtesAsoc><ar:CbteAsoc>")
                    .append(campo("Tipo", a.tipo()))
                    .append(campo("PtoVta", a.puntoVenta()))
                    .append(campo("Nro", a.numero()))
                    .append(campo("Cuit", a.cuit()))
                    .append(campo("CbteFch", FECHA.format(a.fecha())))
                    .append("</ar:CbteAsoc></ar:CbtesAsoc>");
        }

        if (s.alicuotas() != null && !s.alicuotas().isEmpty()) {
            x.append("<ar:Iva>");
            for (Wsfe.AlicuotaIva iva : s.alicuotas()) {
                x.append("<ar:AlicIva>")
                        .append(campo("Id", iva.id()))
                        .append(campo("BaseImp", importe(iva.baseImponible())))
                        .append(campo("Importe", importe(iva.importe())))
                        .append("</ar:AlicIva>");
            }
            x.append("</ar:Iva>");
        }

        return x.toString();
    }

    private static String auth(Wsfe.Auth auth) {
        return "<ar:Auth>"
                + campo("Token", auth.token())
                + campo("Sign", auth.sign())
                + campo("Cuit", auth.cuit())
                + "</ar:Auth>";
    }

    private static String campo(String nombre, Object valor) {
        return "<ar:" + nombre + ">" + Xml.escapar(String.valueOf(valor)) + "</ar:" + nombre + ">";
    }

    /** Importe con dos decimales y punto decimal ("1210.00"). */
    static String importe(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    // ------------------------------------------------------------------
    // Lectura de la respuesta
    // ------------------------------------------------------------------

    private Document llamar(AmbienteArca ambiente, String metodo, String cuerpo) {
        return soap.enviar(properties.urlWsfe(ambiente), NS + metodo, NAMESPACE, cuerpo);
    }

    /** Mensajes {Code, Msg} de los elementos {@code item} dentro de {@code contenedor}. */
    private static List<Wsfe.Mensaje> mensajes(org.w3c.dom.Node raiz, String contenedor, String item) {

        return Xml.elemento(raiz, contenedor)
                .map(c -> Xml.elementos(c, item).stream()
                        .map(e -> new Wsfe.Mensaje(
                                Integer.parseInt(Xml.texto(e, "Code").orElse("0")),
                                Xml.texto(e, "Msg").orElse("")))
                        .toList())
                .orElse(List.of());
    }

    static String unir(List<Wsfe.Mensaje> mensajes) {
        return mensajes.stream().map(Wsfe.Mensaje::toString).collect(Collectors.joining("; "));
    }

    private static LocalDate fecha(String aaaammdd) {
        try {
            return LocalDate.parse(aaaammdd, FECHA);
        } catch (DateTimeParseException e) {
            throw new ArcaException("ARCA devolvió una fecha inválida: " + aaaammdd, e);
        }
    }

}
