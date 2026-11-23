package org.gestionComercio.arca;

import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.enums.TipoDocumento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglasFacturacionTest {

    private static final BigDecimal MONTO = new BigDecimal("10000000");

    @Test
    @DisplayName("Responsable inscripto: A a inscriptos y monotributistas, B al resto")
    void responsableInscripto() {
        CondicionIVA ri = CondicionIVA.RESPONSABLE_INSCRIPTO;

        assertThat(ReglasFacturacion.tipoFactura(ri, CondicionIVA.RESPONSABLE_INSCRIPTO))
                .isEqualTo(TipoComprobante.FACTURA_A);
        assertThat(ReglasFacturacion.tipoFactura(ri, CondicionIVA.MONOTRIBUTISTA)).isEqualTo(TipoComprobante.FACTURA_A);
        assertThat(ReglasFacturacion.tipoFactura(ri, CondicionIVA.CONSUMIDOR_FINAL))
                .isEqualTo(TipoComprobante.FACTURA_B);
        assertThat(ReglasFacturacion.tipoFactura(ri, CondicionIVA.EXENTO)).isEqualTo(TipoComprobante.FACTURA_B);
        assertThat(ReglasFacturacion.tipoFactura(ri, null)).isEqualTo(TipoComprobante.FACTURA_B);
    }

    @Test
    @DisplayName("Monotributista y exento emiten siempre factura C")
    void monotributistaYExento() {
        for (CondicionIVA receptor : CondicionIVA.values()) {
            assertThat(ReglasFacturacion.tipoFactura(CondicionIVA.MONOTRIBUTISTA, receptor))
                    .isEqualTo(TipoComprobante.FACTURA_C);
            assertThat(ReglasFacturacion.tipoFactura(CondicionIVA.EXENTO, receptor))
                    .isEqualTo(TipoComprobante.FACTURA_C);
        }
    }

    @Test
    @DisplayName("El receptor se identifica con el código de ARCA del documento y de la condición")
    void receptor() {
        ReglasFacturacion.Receptor r = ReglasFacturacion.receptor(
                TipoDocumento.CUIT, "20-12345678-6", CondicionIVA.RESPONSABLE_INSCRIPTO);

        assertThat(r.docTipo()).isEqualTo(80);
        assertThat(r.docNro()).isEqualTo(20123456786L);
        assertThat(r.condicionIva().getCodigoArca()).isEqualTo(1);
        assertThat(r.conCuit()).isTrue();

        ReglasFacturacion.Receptor sinCliente = ReglasFacturacion.receptor(null, null, null);
        assertThat(sinCliente.docTipo()).isEqualTo(99);
        assertThat(sinCliente.docNro()).isZero();
        assertThat(sinCliente.condicionIva().getCodigoArca()).isEqualTo(5);
        assertThat(sinCliente.identificado()).isFalse();
    }

    @Test
    @DisplayName("Un documento con letras no se puede enviar a ARCA")
    void documentoConLetras() {
        assertThatThrownBy(() -> ReglasFacturacion.receptor(TipoDocumento.PASAPORTE, "AB123", null))
                .isInstanceOf(ArcaException.class)
                .hasMessageContaining("sólo se admiten números");
    }

    @Test
    @DisplayName("Factura A exige CUIT y que la letra corresponda al cliente")
    void problemasDeLetra() {
        ReglasFacturacion.Receptor dni = ReglasFacturacion.receptor(
                TipoDocumento.DNI, "30123456", CondicionIVA.CONSUMIDOR_FINAL);

        assertThat(ReglasFacturacion.problemas(TipoComprobante.FACTURA_A, CondicionIVA.RESPONSABLE_INSCRIPTO,
                dni, BigDecimal.TEN, MONTO))
                .hasSize(2)
                .anySatisfy(p -> assertThat(p).contains("le emite factura b"))
                .anySatisfy(p -> assertThat(p).contains("CUIT"));

        assertThat(ReglasFacturacion.problemas(TipoComprobante.FACTURA_B, CondicionIVA.RESPONSABLE_INSCRIPTO,
                dni, BigDecimal.TEN, MONTO)).isEmpty();
    }

    @Test
    @DisplayName("Desde el monto límite, el consumidor final tiene que estar identificado")
    void montoConsumidorFinal() {
        ReglasFacturacion.Receptor anonimo = ReglasFacturacion.Receptor.CONSUMIDOR_FINAL_SIN_IDENTIFICAR;

        assertThat(ReglasFacturacion.problemas(TipoComprobante.FACTURA_B, CondicionIVA.RESPONSABLE_INSCRIPTO,
                anonimo, new BigDecimal("9999999.99"), MONTO)).isEmpty();
        assertThat(ReglasFacturacion.problemas(TipoComprobante.FACTURA_B, CondicionIVA.RESPONSABLE_INSCRIPTO,
                anonimo, MONTO, MONTO)).singleElement().asString().contains("identificar al comprador");
    }

}
