package org.gestionComercio.arca;

import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.w3c.dom.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

/**
 * Pedido de acceso al WSAA: arma el TRA (loginTicketRequest), lo firma en
 * CMS (PKCS#7) con el certificado del comercio y lee la respuesta
 * (loginTicketResponse).
 */
public final class AutenticacionWsaa {

    /** Servicio de facturación electrónica. */
    public static final String SERVICIO_WSFE = "wsfe";

    static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    /**
     * La hora de generación va unos minutos en el pasado para tolerar que el
     * reloj de la PC esté adelantado respecto del de ARCA.
     */
    static final Duration TOLERANCIA_RELOJ = Duration.ofMinutes(10);

    private AutenticacionWsaa() {
    }

    /** TRA para el servicio, generado en {@code ahora}. */
    public static String crearTra(String servicio, Instant ahora) {

        DateTimeFormatter formato = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        OffsetDateTime momento = ahora.truncatedTo(ChronoUnit.SECONDS).atZone(ZONA_ARGENTINA).toOffsetDateTime();

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <loginTicketRequest version="1.0">
                <header>
                <uniqueId>%d</uniqueId>
                <generationTime>%s</generationTime>
                <expirationTime>%s</expirationTime>
                </header>
                <service>%s</service>
                </loginTicketRequest>
                """.formatted(
                ahora.getEpochSecond() & 0xFFFFFFFFL,
                formato.format(momento.minus(TOLERANCIA_RELOJ)),
                formato.format(momento.plus(TOLERANCIA_RELOJ)),
                Xml.escapar(servicio));
    }

    /** TRA firmado en CMS (con el contenido incluido) y codificado en base64, listo para loginCms. */
    public static String firmar(String tra, CredencialArca credencial) {

        try {
            CMSSignedDataGenerator generador = new CMSSignedDataGenerator();

            generador.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                    new JcaDigestCalculatorProviderBuilder().build())
                    .build(CertificadosArca.firmante(credencial.clavePrivada()), credencial.certificado()));
            generador.addCertificates(new JcaCertStore(List.of(credencial.certificado())));

            CMSSignedData firmado = generador.generate(
                    new CMSProcessableByteArray(tra.getBytes(StandardCharsets.UTF_8)), true);

            return Base64.getEncoder().encodeToString(firmado.getEncoded());
        } catch (OperatorCreationException | CertificateEncodingException | CMSException | IOException e) {
            throw new ArcaException("No se pudo firmar el pedido de acceso a ARCA con el certificado.", e);
        }
    }

    /** Lee token, sign y vencimiento del loginTicketResponse. */
    public static TicketAcceso leerRespuesta(String loginTicketResponse) {

        Document documento = Xml.parsear(loginTicketResponse);

        try {
            return new TicketAcceso(
                    Xml.textoObligatorio(documento, "token"),
                    Xml.textoObligatorio(documento, "sign"),
                    OffsetDateTime.parse(Xml.textoObligatorio(documento, "generationTime")).toInstant(),
                    OffsetDateTime.parse(Xml.textoObligatorio(documento, "expirationTime")).toInstant());
        } catch (DateTimeParseException e) {
            throw new ArcaException("El ticket de acceso de ARCA tiene una fecha inválida.", e);
        }
    }

}
