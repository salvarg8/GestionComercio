package org.gestionComercio.arca;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class QrArcaTest {

    @Test
    @DisplayName("La URL lleva el JSON de la especificación v1 en base64")
    void urlConJson() {
        QrArca.Datos datos = new QrArca.Datos(LocalDate.of(2026, 11, 22), 30712345671L, 3, 6, 123,
                new BigDecimal("12100.50"), 80, 20123456786L, "76123456789012");

        String url = QrArca.url(datos);

        assertThat(url).startsWith("https://www.afip.gob.ar/fe/qr/?p=");

        String json = new String(Base64.getDecoder().decode(url.substring(QrArca.URL.length())),
                StandardCharsets.UTF_8);

        assertThat(json).isEqualTo("{\"ver\":1,\"fecha\":\"2026-11-22\",\"cuit\":30712345671,\"ptoVta\":3,"
                + "\"tipoCmp\":6,\"nroCmp\":123,\"importe\":12100.5,\"moneda\":\"PES\",\"ctz\":1,"
                + "\"tipoDocRec\":80,\"nroDocRec\":20123456786,\"tipoCodAut\":\"E\",\"codAut\":76123456789012}");
    }

    @Test
    @DisplayName("Al consumidor final sin identificar no se le informa el documento")
    void consumidorFinal() {
        String json = QrArca.json(new QrArca.Datos(LocalDate.of(2026, 1, 2), 30712345671L, 1, 11, 7,
                new BigDecimal("1500.00"), 99, 0, "1"));

        assertThat(json).doesNotContain("tipoDocRec").doesNotContain("nroDocRec").contains("\"importe\":1500,");
    }

}
