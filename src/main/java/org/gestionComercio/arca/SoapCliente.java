package org.gestionComercio.arca;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Envía pedidos SOAP 1.1 a los web services de ARCA y devuelve la respuesta
 * parseada.
 *
 * <p>Registra cada pedido y respuesta en el log {@code org.gestionComercio.arca}
 * (nivel DEBUG) sin datos sensibles: el token, el sign y el ticket firmado se
 * reemplazan por "***".
 */
@Slf4j
@Component
public class SoapCliente {

    private static final String ENVELOPE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" %s>
            <soapenv:Header/>
            <soapenv:Body>%s</soapenv:Body>
            </soapenv:Envelope>""";

    /** Elementos cuyo contenido no se registra en el log. */
    private static final Pattern SENSIBLES = Pattern.compile(
            "(?is)(<(?:[\\w-]+:)?(Token|Sign|in0|loginCmsReturn)>).*?(</(?:[\\w-]+:)?\\2>)");

    private final HttpClient http;
    private final ArcaProperties properties;

    public SoapCliente(ArcaProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * @param url             dirección del servicio
     * @param soapAction      encabezado SOAPAction (puede ser vacío)
     * @param namespaces      declaraciones de namespace del cuerpo (xmlns:p="...")
     * @param cuerpo          contenido del elemento Body
     * @return la respuesta, ya controlado que no sea un SOAP fault
     * @throws ArcaComunicacionException si no hubo respuesta SOAP
     * @throws ArcaException             si ARCA respondió con un SOAP fault
     */
    public Document enviar(String url, String soapAction, String namespaces, String cuerpo) {

        String pedido = ENVELOPE.formatted(namespaces, cuerpo);
        log.debug("ARCA pedido {} {}: {}", url, soapAction, sinDatosSensibles(pedido));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(properties.timeout())
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"" + soapAction + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(pedido, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;

        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new ArcaComunicacionException("ARCA no respondió a tiempo (" + properties.timeout().toSeconds()
                    + " s). Verifique la conexión a internet.", e);
        } catch (IOException e) {
            throw new ArcaComunicacionException("No se pudo conectar con ARCA: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    + ". Verifique la conexión a internet.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ArcaComunicacionException("Se interrumpió la comunicación con ARCA.", e);
        }

        String cuerpoRespuesta = response.body() == null ? "" : response.body();
        log.debug("ARCA respuesta {} HTTP {}: {}", url, response.statusCode(), sinDatosSensibles(cuerpoRespuesta));

        if (!cuerpoRespuesta.contains("Envelope")) {
            throw new ArcaComunicacionException("ARCA respondió HTTP " + response.statusCode()
                    + " sin un mensaje SOAP. El servicio puede no estar disponible.");
        }

        Document documento = Xml.parsear(cuerpoRespuesta);

        if (Xml.elemento(documento, "Fault").isPresent()) {
            String codigo = Xml.texto(documento, "faultcode").orElse("");
            String mensaje = Xml.texto(documento, "faultstring").orElse("error sin descripción");
            throw new ArcaException(traducirFault(codigo, mensaje));
        }

        return documento;
    }

    static String sinDatosSensibles(String xml) {
        return SENSIBLES.matcher(xml).replaceAll("$1***$3");
    }

    /** Mensajes del WSAA más frecuentes en términos del usuario. */
    static String traducirFault(String codigo, String mensaje) {

        String c = codigo == null ? "" : codigo;

        if (c.contains("alreadyAuthenticated")) {
            return "ARCA ya entregó un ticket de acceso vigente que no quedó guardado. Hay que esperar "
                    + "a que venza (hasta 12 horas) para pedir otro. Detalle: " + mensaje;
        }

        if (c.contains("cms.cert.untrusted") || c.contains("cms.cert.invalid") || c.contains("cms.sign.invalid")) {
            return "ARCA no reconoce el certificado: verifique que sea del ambiente configurado "
                    + "(homologación o producción) y que no esté vencido. Detalle: " + mensaje;
        }

        if (c.contains("notAuthorized") || mensaje.contains("no autorizado")) {
            return "El certificado no está asociado al servicio de facturación electrónica en el "
                    + "Administrador de Relaciones de Clave Fiscal. Detalle: " + mensaje;
        }

        if (c.contains("xml.generationTime.invalid") || c.contains("expirationTime")) {
            return "ARCA rechazó la hora del pedido: verifique que el reloj de la PC esté bien sincronizado. "
                    + "Detalle: " + mensaje;
        }

        return "ARCA devolvió un error: " + mensaje + (c.isBlank() ? "" : " (" + c + ")");
    }

}
