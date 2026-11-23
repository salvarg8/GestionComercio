package org.gestionComercio.arca;

import org.gestionComercio.enums.AmbienteArca;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Configuración técnica de la facturación electrónica (prefijo {@code arca.}
 * en application.properties). Los datos del comercio (ambiente, punto de
 * venta, certificado) están en la base: ver {@code ConfiguracionArca}.
 *
 * @param claveCertificado                clave con que se cifra el certificado guardado en la
 *                                        base; la misma en todas las PC del comercio
 * @param timeout                         tiempo máximo de espera de cada llamada a ARCA
 * @param montoIdentificacionConsumidorFinal desde este total, una factura a consumidor final
 *                                        tiene que identificar al comprador
 * @param urlWsaa                         reemplaza la URL del WSAA del ambiente (vacía: la oficial)
 * @param urlWsfe                         reemplaza la URL del WSFEv1 del ambiente (vacía: la oficial)
 */
@ConfigurationProperties(prefix = "arca")
public record ArcaProperties(
        String claveCertificado,
        Duration timeout,
        BigDecimal montoIdentificacionConsumidorFinal,
        String urlWsaa,
        String urlWsfe
) {

    public ArcaProperties {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            timeout = Duration.ofSeconds(30);
        }
        if (montoIdentificacionConsumidorFinal == null) {
            montoIdentificacionConsumidorFinal = new BigDecimal("10000000");
        }
    }

    public String urlWsaa(AmbienteArca ambiente) {
        return urlWsaa == null || urlWsaa.isBlank() ? ambiente.getUrlWsaa() : urlWsaa;
    }

    public String urlWsfe(AmbienteArca ambiente) {
        return urlWsfe == null || urlWsfe.isBlank() ? ambiente.getUrlWsfe() : urlWsfe;
    }

    /** @return true si está configurada la clave del certificado. */
    public boolean tieneClaveCertificado() {
        return claveCertificado != null && !claveCertificado.isBlank();
    }

}
