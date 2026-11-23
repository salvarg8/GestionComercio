package org.gestionComercio.arca;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificadosYWsaaTest {

    private static final String CUIT = "30712345671";

    @Test
    @DisplayName("El pedido de certificado lleva C=AR, la razón social, el alias y el CUIT")
    void pedidoDeCertificado() throws Exception {
        CertificadosArca.PedidoCertificado pedido = CertificadosArca.generarPedido(CUIT, "El Centro SRL", "caja1");

        assertThat(pedido.csrPem()).startsWith("-----BEGIN CERTIFICATE REQUEST-----");

        try (PEMParser parser = new PEMParser(new StringReader(pedido.csrPem()))) {
            PKCS10CertificationRequest csr = (PKCS10CertificationRequest) parser.readObject();
            assertThat(csr.getSubject().toString())
                    .contains("C=AR").contains("O=El Centro SRL").contains("CN=caja1").contains("CUIT " + CUIT);
        }
    }

    @Test
    @DisplayName("El certificado firmado se reconoce como del pedido y se lee el CUIT del titular")
    void certificadoDelPedido() {
        CertificadosArca.PedidoCertificado pedido = CertificadosArca.generarPedido(CUIT, "El Centro SRL", "caja1");
        X509Certificate certificado = CertificadosDePrueba.firmar(pedido.csrPem(), Duration.ofDays(365));

        assertThat(CertificadosArca.corresponde(certificado, pedido.claves().getPublic())).isTrue();
        assertThat(CertificadosArca.cuitDelTitular(certificado)).contains(CUIT);
        assertThat(CertificadosArca.titular(certificado)).contains("CUIT " + CUIT);

        CertificadosArca.PedidoCertificado otro = CertificadosArca.generarPedido(CUIT, "El Centro SRL", "caja1");
        assertThat(CertificadosArca.corresponde(certificado, otro.claves().getPublic())).isFalse();
    }

    @Test
    @DisplayName("El PKCS#12 cifrado se vuelve a abrir sólo con la misma clave")
    void pkcs12() {
        CredencialArca credencial = CertificadosDePrueba.credencial(CUIT);
        byte[] p12 = CertificadosArca.crearPkcs12(credencial.clavePrivada(), credencial.certificado(),
                "clave".toCharArray());

        CredencialArca leida = CertificadosArca.leerPkcs12(p12, "clave".toCharArray());

        assertThat(leida.certificado()).isEqualTo(credencial.certificado());
        assertThat(leida.clavePrivada().getEncoded()).isEqualTo(credencial.clavePrivada().getEncoded());
        assertThatThrownBy(() -> CertificadosArca.leerPkcs12(p12, "otra".toCharArray()))
                .isInstanceOf(ArcaException.class);
    }

    @Test
    @DisplayName("El certificado se lee en PEM y en DER; otra cosa se rechaza")
    void leerCertificado() throws Exception {
        X509Certificate certificado = CertificadosDePrueba.credencial(CUIT).certificado();
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder().encodeToString(certificado.getEncoded())
                + "\n-----END CERTIFICATE-----\n";

        assertThat(CertificadosArca.leerCertificado(certificado.getEncoded())).isEqualTo(certificado);
        assertThat(CertificadosArca.leerCertificado(pem.getBytes(StandardCharsets.US_ASCII))).isEqualTo(certificado);
        assertThatThrownBy(() -> CertificadosArca.leerCertificado("hola".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ArcaException.class);
    }

    @Test
    @DisplayName("El TRA pide el servicio con la hora de generación en el pasado y la de vencimiento en el futuro")
    void tra() {
        Instant ahora = Instant.parse("2026-11-22T15:00:00Z");
        String tra = AutenticacionWsaa.crearTra("wsfe", ahora);

        assertThat(tra).contains("<service>wsfe</service>")
                .contains("<uniqueId>" + ahora.getEpochSecond() + "</uniqueId>")
                .contains("<generationTime>2026-11-22T11:50:00-03:00</generationTime>")
                .contains("<expirationTime>2026-11-22T12:10:00-03:00</expirationTime>");
    }

    @Test
    @DisplayName("El TRA firmado es un CMS válido con el contenido y el certificado incluidos")
    void firmaCms() throws Exception {
        CredencialArca credencial = CertificadosDePrueba.credencial(CUIT);
        String tra = AutenticacionWsaa.crearTra("wsfe", Instant.now());

        CMSSignedData cms = new CMSSignedData(Base64.getDecoder().decode(AutenticacionWsaa.firmar(tra, credencial)));

        assertThat(new String((byte[]) cms.getSignedContent().getContent(), StandardCharsets.UTF_8)).isEqualTo(tra);

        SignerInformation firmante = cms.getSignerInfos().getSigners().iterator().next();
        X509CertificateHolder incluido = (X509CertificateHolder) cms.getCertificates()
                .getMatches(firmante.getSID()).iterator().next();

        assertThat(incluido.getEncoded()).isEqualTo(credencial.certificado().getEncoded());
        assertThat(firmante.verify(new JcaSimpleSignerInfoVerifierBuilder().build(credencial.certificado())))
                .isTrue();
    }

    @Test
    @DisplayName("Lee token, sign y vencimiento del loginTicketResponse")
    void respuestaWsaa() {
        TicketAcceso ticket = AutenticacionWsaa.leerRespuesta("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <loginTicketResponse version="1.0">
                  <header>
                    <source>CN=wsaahomo, O=AFIP, C=AR</source>
                    <destination>SERIALNUMBER=CUIT 30712345671, CN=caja1</destination>
                    <uniqueId>1</uniqueId>
                    <generationTime>2026-11-22T11:50:01.123-03:00</generationTime>
                    <expirationTime>2026-11-22T23:50:01.123-03:00</expirationTime>
                  </header>
                  <credentials>
                    <token>TOKEN123</token>
                    <sign>SIGN456</sign>
                  </credentials>
                </loginTicketResponse>
                """);

        assertThat(ticket.token()).isEqualTo("TOKEN123");
        assertThat(ticket.sign()).isEqualTo("SIGN456");
        assertThat(ticket.vence()).isEqualTo(OffsetDateTime.parse("2026-11-22T23:50:01.123-03:00").toInstant());
        assertThat(ticket.vigente(ticket.vence().minus(Duration.ofMinutes(11)))).isTrue();
        assertThat(ticket.vigente(ticket.vence().minus(Duration.ofMinutes(9)))).isFalse();
    }

}
