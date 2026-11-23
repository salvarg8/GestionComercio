package org.gestionComercio.arca;

import com.sun.net.httpserver.HttpServer;
import org.gestionComercio.enums.AmbienteArca;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El cliente SOAP contra un servidor local que responde como el WSFEv1. */
class WsfeClienteSoapTest {

    private static final Wsfe.Auth AUTH = new Wsfe.Auth("TOKEN", "SIGN", 30712345671L);

    private HttpServer servidor;
    private volatile String respuesta;
    private volatile int status;
    private volatile String pedido;
    private volatile String soapAction;
    private WsfeClienteSoap cliente;

    @BeforeEach
    void setUp() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/wsfe", intercambio -> {
            pedido = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            soapAction = intercambio.getRequestHeaders().getFirst("SOAPAction");
            byte[] cuerpo = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(status, cuerpo.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(cuerpo);
            }
        });
        servidor.start();
        status = 200;

        ArcaProperties properties = new ArcaProperties("x", Duration.ofSeconds(5), null, null,
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/wsfe");
        cliente = new WsfeClienteSoap(new SoapCliente(properties), properties);
    }

    @AfterEach
    void tearDown() {
        servidor.stop(0);
    }

    private static String sobre(String cuerpo) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                + cuerpo + "</soap:Body></soap:Envelope>";
    }

    private static Wsfe.SolicitudCae solicitud(Wsfe.ComprobanteAsociado asociado) {
        return new Wsfe.SolicitudCae(3, 6, 124, LocalDate.of(2026, 11, 22), 99, 0, 5,
                new BigDecimal("1210"), BigDecimal.ZERO, new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("210"), List.of(new Wsfe.AlicuotaIva(5, new BigDecimal("1000"), new BigDecimal("210"))),
                asociado);
    }

    @Test
    @DisplayName("FECompUltimoAutorizado: envía Auth, punto de venta y tipo; lee el número")
    void ultimoAutorizado() {
        respuesta = sobre("<FECompUltimoAutorizadoResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<FECompUltimoAutorizadoResult><PtoVta>3</PtoVta><CbteTipo>6</CbteTipo><CbteNro>123</CbteNro>"
                + "</FECompUltimoAutorizadoResult></FECompUltimoAutorizadoResponse>");

        assertThat(cliente.ultimoAutorizado(AmbienteArca.HOMOLOGACION, AUTH, 3, 6)).isEqualTo(123);
        assertThat(soapAction).isEqualTo("\"http://ar.gov.afip.dif.FEV1/FECompUltimoAutorizado\"");
        assertThat(pedido).contains("<ar:Token>TOKEN</ar:Token>").contains("<ar:Cuit>30712345671</ar:Cuit>")
                .contains("<ar:PtoVta>3</ar:PtoVta>").contains("<ar:CbteTipo>6</ar:CbteTipo>");
    }

    @Test
    @DisplayName("FECompUltimoAutorizado con error de ARCA lanza el motivo")
    void ultimoAutorizadoConError() {
        respuesta = sobre("<FECompUltimoAutorizadoResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\">"
                + "<FECompUltimoAutorizadoResult><PtoVta>3</PtoVta><CbteTipo>6</CbteTipo><CbteNro>0</CbteNro>"
                + "<Errors><Err><Code>600</Code><Msg>ValidacionDeToken: No validaron las fechas del token</Msg>"
                + "</Err></Errors></FECompUltimoAutorizadoResult></FECompUltimoAutorizadoResponse>");

        assertThatThrownBy(() -> cliente.ultimoAutorizado(AmbienteArca.HOMOLOGACION, AUTH, 3, 6))
                .isInstanceOf(ArcaException.class)
                .hasMessageContaining("600: ValidacionDeToken");
    }

    @Test
    @DisplayName("FECAESolicitar aprobado: arma el detalle completo y lee CAE y vencimiento")
    void caeAprobado() {
        respuesta = sobre("<FECAESolicitarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECAESolicitarResult>"
                + "<FeCabResp><Cuit>30712345671</Cuit><PtoVta>3</PtoVta><CbteTipo>6</CbteTipo>"
                + "<FchProceso>20261122120000</FchProceso><CantReg>1</CantReg><Resultado>A</Resultado>"
                + "<Reproceso>N</Reproceso></FeCabResp>"
                + "<FeDetResp><FECAEDetResponse><Concepto>1</Concepto><DocTipo>99</DocTipo><DocNro>0</DocNro>"
                + "<CbteDesde>124</CbteDesde><CbteHasta>124</CbteHasta><CbteFch>20261122</CbteFch>"
                + "<Resultado>A</Resultado><Observaciones><Obs><Code>10217</Code><Msg>Aviso de prueba</Msg></Obs>"
                + "</Observaciones><CAE>76123456789012</CAE><CAEFchVto>20261202</CAEFchVto>"
                + "</FECAEDetResponse></FeDetResp></FECAESolicitarResult></FECAESolicitarResponse>");

        Wsfe.RespuestaCae r = cliente.solicitarCae(AmbienteArca.HOMOLOGACION, AUTH, solicitud(null));

        assertThat(r.aprobado()).isTrue();
        assertThat(r.cae()).isEqualTo("76123456789012");
        assertThat(r.vencimientoCae()).isEqualTo(LocalDate.of(2026, 12, 2));
        assertThat(r.observaciones()).containsExactly(new Wsfe.Mensaje(10217, "Aviso de prueba"));
        assertThat(pedido)
                .contains("<ar:CantReg>1</ar:CantReg>")
                .contains("<ar:CbteDesde>124</ar:CbteDesde><ar:CbteHasta>124</ar:CbteHasta>")
                .contains("<ar:CbteFch>20261122</ar:CbteFch>")
                .contains("<ar:ImpTotal>1210.00</ar:ImpTotal>")
                .contains("<ar:ImpNeto>1000.00</ar:ImpNeto>")
                .contains("<ar:ImpIVA>210.00</ar:ImpIVA>")
                .contains("<ar:MonId>PES</ar:MonId>")
                .contains("<ar:CondicionIVAReceptorId>5</ar:CondicionIVAReceptorId>")
                .contains("<ar:AlicIva><ar:Id>5</ar:Id><ar:BaseImp>1000.00</ar:BaseImp><ar:Importe>210.00</ar:Importe>")
                .doesNotContain("CbtesAsoc");
    }

    @Test
    @DisplayName("FECAESolicitar de una nota de crédito incluye el comprobante asociado")
    void notaCreditoConAsociado() {
        respuesta = sobre("<FECAESolicitarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECAESolicitarResult>"
                + "<FeDetResp><FECAEDetResponse><Resultado>A</Resultado><CAE>1</CAE><CAEFchVto>20261202</CAEFchVto>"
                + "</FECAEDetResponse></FeDetResp></FECAESolicitarResult></FECAESolicitarResponse>");

        cliente.solicitarCae(AmbienteArca.HOMOLOGACION, AUTH,
                solicitud(new Wsfe.ComprobanteAsociado(6, 3, 120, 30712345671L, LocalDate.of(2026, 11, 20))));

        assertThat(pedido).contains("<ar:CbtesAsoc><ar:CbteAsoc><ar:Tipo>6</ar:Tipo><ar:PtoVta>3</ar:PtoVta>"
                + "<ar:Nro>120</ar:Nro><ar:Cuit>30712345671</ar:Cuit><ar:CbteFch>20261120</ar:CbteFch>");
    }

    @Test
    @DisplayName("FECAESolicitar rechazado: devuelve el motivo sin lanzar excepción")
    void caeRechazado() {
        respuesta = sobre("<FECAESolicitarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECAESolicitarResult>"
                + "<FeCabResp><Resultado>R</Resultado></FeCabResp>"
                + "<FeDetResp><FECAEDetResponse><Resultado>R</Resultado><Observaciones><Obs><Code>10242</Code>"
                + "<Msg>El campo Condicion Frente al IVA del receptor es obligatorio</Msg></Obs></Observaciones>"
                + "<CAE/><CAEFchVto/></FECAEDetResponse></FeDetResp></FECAESolicitarResult></FECAESolicitarResponse>");

        Wsfe.RespuestaCae r = cliente.solicitarCae(AmbienteArca.HOMOLOGACION, AUTH, solicitud(null));

        assertThat(r.aprobado()).isFalse();
        assertThat(r.resultado()).isEqualTo("R");
        assertThat(r.cae()).isNull();
        assertThat(r.observaciones()).extracting(Wsfe.Mensaje::codigo).containsExactly(10242);
    }

    @Test
    @DisplayName("FECAESolicitar con error de autenticación y sin detalle")
    void caeSinProcesar() {
        respuesta = sobre("<FECAESolicitarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECAESolicitarResult>"
                + "<Errors><Err><Code>600</Code><Msg>ValidacionDeToken</Msg></Err></Errors>"
                + "</FECAESolicitarResult></FECAESolicitarResponse>");

        Wsfe.RespuestaCae r = cliente.solicitarCae(AmbienteArca.HOMOLOGACION, AUTH, solicitud(null));

        assertThat(r.resultado()).isNull();
        assertThat(r.errorDeAutenticacion()).isTrue();
    }

    @Test
    @DisplayName("FECompConsultar: lee el comprobante emitido, o vacío si ARCA no lo tiene (602)")
    void consultar() {
        respuesta = sobre("<FECompConsultarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECompConsultarResult>"
                + "<ResultGet><Concepto>1</Concepto><DocTipo>99</DocTipo><DocNro>0</DocNro><CbteDesde>124</CbteDesde>"
                + "<CbteHasta>124</CbteHasta><CbteFch>20261122</CbteFch><ImpTotal>1210</ImpTotal>"
                + "<Resultado>A</Resultado><CodAutorizacion>76123456789012</CodAutorizacion>"
                + "<EmisionTipo>CAE</EmisionTipo><FchVto>20261202</FchVto><PtoVta>3</PtoVta><CbteTipo>6</CbteTipo>"
                + "</ResultGet></FECompConsultarResult></FECompConsultarResponse>");

        Optional<Wsfe.ComprobanteEmitido> emitido = cliente.consultar(AmbienteArca.HOMOLOGACION, AUTH, 3, 6, 124);

        assertThat(emitido).hasValueSatisfying(e -> {
            assertThat(e.numero()).isEqualTo(124);
            assertThat(e.cae()).isEqualTo("76123456789012");
            assertThat(e.importeTotal()).isEqualByComparingTo("1210");
            assertThat(e.fecha()).isEqualTo(LocalDate.of(2026, 11, 22));
        });

        respuesta = sobre("<FECompConsultarResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FECompConsultarResult>"
                + "<Errors><Err><Code>602</Code><Msg>No existen datos en nuestros registros para los parametros "
                + "ingresados.</Msg></Err></Errors></FECompConsultarResult></FECompConsultarResponse>");

        assertThat(cliente.consultar(AmbienteArca.HOMOLOGACION, AUTH, 3, 6, 125)).isEmpty();
    }

    @Test
    @DisplayName("FEDummy informa el estado de los servidores")
    void estado() {
        respuesta = sobre("<FEDummyResponse xmlns=\"http://ar.gov.afip.dif.FEV1/\"><FEDummyResult>"
                + "<AppServer>OK</AppServer><DbServer>OK</DbServer><AuthServer>OK</AuthServer>"
                + "</FEDummyResult></FEDummyResponse>");

        assertThat(cliente.estado(AmbienteArca.HOMOLOGACION).todosOk()).isTrue();
    }

    @Test
    @DisplayName("Un SOAP fault se informa como error de ARCA; un error HTTP sin SOAP, como falta de comunicación")
    void errores() {
        status = 500;
        respuesta = sobre("<soap:Fault><faultcode>soap:Server</faultcode>"
                + "<faultstring>Error interno</faultstring></soap:Fault>");

        assertThatThrownBy(() -> cliente.estado(AmbienteArca.HOMOLOGACION))
                .isInstanceOf(ArcaException.class)
                .isNotInstanceOf(ArcaComunicacionException.class)
                .hasMessageContaining("Error interno");

        status = 503;
        respuesta = "<html>Service Unavailable</html>";

        assertThatThrownBy(() -> cliente.estado(AmbienteArca.HOMOLOGACION))
                .isInstanceOf(ArcaComunicacionException.class);
    }

    @Test
    @DisplayName("Sin servidor: falta de comunicación")
    void sinServidor() {
        servidor.stop(0);

        assertThatThrownBy(() -> cliente.estado(AmbienteArca.HOMOLOGACION))
                .isInstanceOf(ArcaComunicacionException.class);
    }

    @Test
    @DisplayName("El log no muestra token, sign ni el ticket firmado")
    void sinDatosSensibles() {
        String xml = "<ar:Auth><ar:Token>SECRETO</ar:Token><ar:Sign>FIRMA</ar:Sign><ar:Cuit>1</ar:Cuit></ar:Auth>"
                + "<wsaa:in0>CMS</wsaa:in0><loginCmsReturn>&lt;token&gt;X</loginCmsReturn>";

        assertThat(SoapCliente.sinDatosSensibles(xml))
                .doesNotContain("SECRETO").doesNotContain("FIRMA").doesNotContain("CMS")
                .doesNotContain("&lt;token&gt;X")
                .contains("<ar:Token>***</ar:Token>").contains("<ar:Cuit>1</ar:Cuit>");
    }

}
