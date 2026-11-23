package org.gestionComercio.arca;

import org.gestionComercio.enums.AlicuotaIVA;
import org.gestionComercio.enums.TipoComprobante;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportesArcaTest {

    private static ImportesArca.Renglon renglon(AlicuotaIVA alicuota, String importe) {
        return new ImportesArca.Renglon(alicuota, new BigDecimal(importe));
    }

    /** Neto + IVA + exento tiene que dar exactamente el total. */
    private static void cuadra(ImportesArca.Importes i) {
        assertThat(i.neto().add(i.iva()).add(i.exento())).isEqualByComparingTo(i.total());
        assertThat(i.alicuotas().stream().map(Wsfe.AlicuotaIva::baseImponible).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(i.neto());
        assertThat(i.alicuotas().stream().map(Wsfe.AlicuotaIva::importe).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(i.iva());
    }

    @Test
    @DisplayName("Un renglón al 21 %: neto = total / 1,21 e IVA la diferencia")
    void alicuotaUnica() {
        ImportesArca.Importes i = ImportesArca.calcular(TipoComprobante.FACTURA_B,
                List.of(renglon(AlicuotaIVA.IVA_21, "1210.00")), BigDecimal.ZERO);

        assertThat(i.total()).isEqualByComparingTo("1210.00");
        assertThat(i.neto()).isEqualByComparingTo("1000.00");
        assertThat(i.iva()).isEqualByComparingTo("210.00");
        assertThat(i.exento()).isEqualByComparingTo("0");
        assertThat(i.alicuotas()).containsExactly(
                new Wsfe.AlicuotaIva(5, new BigDecimal("1000.00"), new BigDecimal("210.00")));
    }

    @Test
    @DisplayName("Varias alícuotas, exento y descuento: el descuento se reparte y todo cuadra al centavo")
    void variasAlicuotasConDescuento() {
        ImportesArca.Importes i = ImportesArca.calcular(TipoComprobante.FACTURA_A, List.of(
                renglon(AlicuotaIVA.IVA_21, "999.99"),
                renglon(AlicuotaIVA.IVA_105, "333.33"),
                renglon(AlicuotaIVA.EXENTO, "100.01"),
                renglon(AlicuotaIVA.IVA_21, "0.01")), new BigDecimal("77.77"));

        assertThat(i.total()).isEqualByComparingTo("1355.57");
        assertThat(i.alicuotas()).extracting(Wsfe.AlicuotaIva::id).containsExactly(4, 5);
        assertThat(i.exento()).isPositive();
        cuadra(i);
    }

    @Test
    @DisplayName("Muchos importes con decimales: siempre cuadra con el total")
    void cuadraSiempre() {
        for (int centavos = 1; centavos < 2000; centavos += 37) {
            BigDecimal a = BigDecimal.valueOf(centavos, 2).add(new BigDecimal("13.07"));
            BigDecimal b = BigDecimal.valueOf(centavos * 3L, 2);

            ImportesArca.Importes i = ImportesArca.calcular(TipoComprobante.FACTURA_B, List.of(
                    new ImportesArca.Renglon(AlicuotaIVA.IVA_21, a),
                    new ImportesArca.Renglon(AlicuotaIVA.IVA_27, b)), BigDecimal.valueOf(centavos % 500, 2));

            cuadra(i);
        }
    }

    @Test
    @DisplayName("Factura C: no discrimina IVA, el neto es el total")
    void facturaC() {
        ImportesArca.Importes i = ImportesArca.calcular(TipoComprobante.FACTURA_C,
                List.of(renglon(AlicuotaIVA.IVA_21, "1210.00"), renglon(AlicuotaIVA.EXENTO, "50")),
                new BigDecimal("10"));

        assertThat(i.total()).isEqualByComparingTo("1250.00");
        assertThat(i.neto()).isEqualByComparingTo("1250.00");
        assertThat(i.iva()).isEqualByComparingTo("0");
        assertThat(i.exento()).isEqualByComparingTo("0");
        assertThat(i.alicuotas()).isEmpty();
    }

    @Test
    @DisplayName("Todo exento: sin alícuotas de IVA, el total va como operación exenta")
    void todoExento() {
        ImportesArca.Importes i = ImportesArca.calcular(TipoComprobante.FACTURA_B,
                List.of(renglon(AlicuotaIVA.EXENTO, "500.00")), BigDecimal.ZERO);

        assertThat(i.exento()).isEqualByComparingTo("500.00");
        assertThat(i.neto()).isEqualByComparingTo("0");
        assertThat(i.alicuotas()).isEmpty();
    }

}
