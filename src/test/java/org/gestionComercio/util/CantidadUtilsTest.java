package org.gestionComercio.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CantidadUtilsTest {

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void esEnteraIgnoraCerosDecimales() {
        assertThat(CantidadUtils.esEntera(bd("5"))).isTrue();
        assertThat(CantidadUtils.esEntera(bd("5.000"))).isTrue();
        assertThat(CantidadUtils.esEntera(bd("0.000"))).isTrue();
        assertThat(CantidadUtils.esEntera(bd("1E+2"))).isTrue();
        assertThat(CantidadUtils.esEntera(bd("5.5"))).isFalse();
    }

    @Test
    void escalaValidaHastaTresDecimalesSignificativos() {
        assertThat(CantidadUtils.escalaValida(bd("0.125"))).isTrue();
        assertThat(CantidadUtils.escalaValida(bd("0.12500"))).isTrue();
        assertThat(CantidadUtils.escalaValida(bd("0.1255"))).isFalse();
    }

    @Test
    void dentroDelMaximoAdmiteDoceDigitosEnteros() {
        assertThat(CantidadUtils.dentroDelMaximo(bd("999999999999.999"))).isTrue();
        assertThat(CantidadUtils.dentroDelMaximo(bd("-999999999999.999"))).isTrue();
        assertThat(CantidadUtils.dentroDelMaximo(bd("1000000000000"))).isFalse();
    }

    @Test
    void normalizarLlevaATresDecimales() {
        assertThat(CantidadUtils.normalizar(bd("2.5"))).isEqualTo(bd("2.500"));
        assertThat(CantidadUtils.normalizar(null)).isEqualTo(bd("0.000"));
        assertThatThrownBy(() -> CantidadUtils.normalizar(bd("0.0001"))).isInstanceOf(ArithmeticException.class);
    }

    @Test
    void formatearUsaFormatoArgentinoSinCerosDeMas() {
        assertThat(CantidadUtils.formatear(bd("1234.500"))).isEqualTo("1.234,5");
        assertThat(CantidadUtils.formatear(bd("10.000"))).isEqualTo("10");
        assertThat(CantidadUtils.formatear(bd("-0.750"))).isEqualTo("-0,75");
        assertThat(CantidadUtils.formatear(null)).isEqualTo("0");
    }

    @Test
    void formatearEditableNoUsaSeparadorDeMiles() {
        assertThat(CantidadUtils.formatearEditable(bd("1234.500"))).isEqualTo("1234,5");
        assertThat(CantidadUtils.formatearEditable(null)).isEmpty();
    }

}
