package org.gestionComercio.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RedondeoTest {

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("Hacia abajo, al múltiplo de cada opción y con dos decimales")
    void haciaAbajo() {
        assertThat(Redondeo.SIN_REDONDEO.haciaAbajo(bd("1234.567"))).isEqualByComparingTo("1234.57");
        assertThat(Redondeo.DIEZ_CENTAVOS.haciaAbajo(bd("1234.56"))).isEqualByComparingTo("1234.50");
        assertThat(Redondeo.UN_PESO.haciaAbajo(bd("1234.99"))).isEqualByComparingTo("1234");
        assertThat(Redondeo.DIEZ_PESOS.haciaAbajo(bd("1239.99"))).isEqualByComparingTo("1230");
        assertThat(Redondeo.CIEN_PESOS.haciaAbajo(bd("1299"))).isEqualByComparingTo("1200");
        assertThat(Redondeo.UN_PESO.haciaAbajo(bd("1234.00")).scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Al más cercano, con la mitad hacia arriba")
    void alMasCercano() {
        assertThat(Redondeo.DIEZ_PESOS.alMasCercano(bd("1234.99"))).isEqualByComparingTo("1230");
        assertThat(Redondeo.DIEZ_PESOS.alMasCercano(bd("1235"))).isEqualByComparingTo("1240");
        assertThat(Redondeo.UN_PESO.alMasCercano(bd("99.49"))).isEqualByComparingTo("99");
        assertThat(Redondeo.SIN_REDONDEO.alMasCercano(bd("10.005"))).isEqualByComparingTo("10.01");
    }

    @Test
    @DisplayName("Hacia arriba, al múltiplo")
    void haciaArriba() {
        assertThat(Redondeo.DIEZ_PESOS.haciaArriba(bd("3"))).isEqualByComparingTo("10");
        assertThat(Redondeo.DIEZ_PESOS.haciaArriba(bd("10"))).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("El total de una venta baja al múltiplo, pero nunca a cero")
    void totalDeVenta() {
        assertThat(Redondeo.CIEN_PESOS.totalDeVenta(bd("1250"))).isEqualByComparingTo("1200");
        assertThat(Redondeo.CIEN_PESOS.totalDeVenta(bd("50"))).isEqualByComparingTo("50");
        assertThat(Redondeo.CIEN_PESOS.totalDeVenta(BigDecimal.ZERO)).isEqualByComparingTo("0");
        assertThat(Redondeo.UN_PESO.totalDeVenta(null)).isNull();
    }
}
