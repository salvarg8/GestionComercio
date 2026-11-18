package org.gestionComercio.controller.caja;

import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.util.CurrencyUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formato y validaciones de las pantallas de caja, sin abrir ventanas
 * (no requiere el toolkit de JavaFX).
 */
class CajaFormatoTest {

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private static String pesos(String valor) {
        return CurrencyUtils.format(bd(valor));
    }

    @Test
    @DisplayName("los ingresos llevan +, los egresos − y la apertura no lleva signo")
    void importeConSigno() {

        assertThat(CajaFormato.importeConSigno(TipoMovimientoCaja.INGRESO_MANUAL, bd("10"))).isEqualTo("+ " + pesos("10"));
        assertThat(CajaFormato.importeConSigno(TipoMovimientoCaja.RETIRO, bd("10"))).isEqualTo("− " + pesos("10"));
        assertThat(CajaFormato.importeConSigno(TipoMovimientoCaja.APERTURA, bd("10"))).isEqualTo(pesos("10"));
        assertThat(CajaFormato.importeConSigno(TipoMovimientoCaja.GASTO, null)).isEmpty();
    }

    @Test
    @DisplayName("la diferencia se informa como sobrante, faltante o sin diferencia")
    void textoDiferencia() {

        assertThat(CajaFormato.textoDiferencia(bd("0.00"))).isEqualTo("Sin diferencia");
        assertThat(CajaFormato.textoDiferencia(bd("20"))).isEqualTo("Sobrante " + pesos("20"));
        assertThat(CajaFormato.textoDiferencia(bd("-50"))).isEqualTo("Faltante " + pesos("50"));
        assertThat(CajaFormato.textoDiferencia(null)).isEmpty();

        assertThat(CajaFormato.estiloDiferencia(bd("0"))).isEqualTo("estado-activo");
        assertThat(CajaFormato.estiloDiferencia(bd("1"))).isEqualTo("movimiento-ajuste");
        assertThat(CajaFormato.estiloDiferencia(bd("-1"))).isEqualTo("stock-bajo");
    }

    @Test
    @DisplayName("el estilo del tipo distingue ingresos, egresos y apertura")
    void estiloTipo() {

        assertThat(CajaFormato.estilo(TipoMovimientoCaja.VENTA)).isEqualTo("estado-activo");
        assertThat(CajaFormato.estilo(TipoMovimientoCaja.GASTO)).isEqualTo("stock-bajo");
        assertThat(CajaFormato.estilo(TipoMovimientoCaja.APERTURA)).isEqualTo("movimiento-ajuste");
        assertThat(CajaFormato.estilo(TipoMovimientoCaja.CIERRE)).isEqualTo("estado-inactivo");
    }

    @Test
    @DisplayName("el usuario se muestra con nombre y login, o sólo login si no tiene nombre")
    void usuario() {

        assertThat(CajaFormato.usuario("Ana Pérez", "ana")).isEqualTo("Ana Pérez (ana)");
        assertThat(CajaFormato.usuario("  ", "ana")).isEqualTo("ana");
        assertThat(CajaFormato.usuario(null, null)).isEmpty();
    }

    @Test
    @DisplayName("errorImporte valida obligatorio, signo, decimales y máximo")
    void errorImporte() {

        assertThat(CajaFormato.errorImporte(null, "El importe", false)).isEqualTo("El importe es obligatorio.");
        assertThat(CajaFormato.errorImporte(bd("0"), "El importe", false)).contains("mayor que cero");
        assertThat(CajaFormato.errorImporte(bd("0"), "El saldo", true)).isNull();
        assertThat(CajaFormato.errorImporte(bd("-1"), "El saldo", true)).contains("negativo");
        assertThat(CajaFormato.errorImporte(bd("1.234"), "El importe", false)).contains("dos decimales");
        assertThat(CajaFormato.errorImporte(bd("1.50"), "El importe", false)).isNull();
        assertThat(CajaFormato.errorImporte(bd("10000000000"), "El importe", false)).contains("máximo");
    }

    @Test
    @DisplayName("el formulario de movimiento ofrece sólo los tipos manuales")
    void tiposManuales() {

        assertThat(MovimientoCajaDialog.tiposManuales()).containsExactly(
                TipoMovimientoCaja.GASTO, TipoMovimientoCaja.RETIRO,
                TipoMovimientoCaja.INGRESO_MANUAL, TipoMovimientoCaja.EGRESO_MANUAL);
    }

    @Test
    @DisplayName("el saldo resultante suma ingresos y resta egresos")
    void saldoResultante() {

        assertThat(MovimientoCajaDialog.calcularSaldoResultante(bd("100"), TipoMovimientoCaja.INGRESO_MANUAL, bd("25")))
                .isEqualByComparingTo("125");
        assertThat(MovimientoCajaDialog.calcularSaldoResultante(bd("100"), TipoMovimientoCaja.GASTO, bd("25")))
                .isEqualByComparingTo("75");
    }

    @Test
    @DisplayName("un egreso no puede superar el saldo; un ingreso siempre es posible")
    void validarSaldo() {

        assertThat(MovimientoCajaDialog.validarSaldo(bd("100"), TipoMovimientoCaja.RETIRO, bd("100.01"))).isNotNull();
        assertThat(MovimientoCajaDialog.validarSaldo(bd("100"), TipoMovimientoCaja.RETIRO, bd("100"))).isNull();
        assertThat(MovimientoCajaDialog.validarSaldo(bd("0"), TipoMovimientoCaja.INGRESO_MANUAL, bd("500"))).isNull();
    }

    @Test
    @DisplayName("la diferencia de arqueo es contado − esperado")
    void diferenciaArqueo() {

        assertThat(CierreCajaDialog.calcularDiferencia(bd("1300"), bd("1250"))).isEqualByComparingTo("-50");
        assertThat(CierreCajaDialog.calcularDiferencia(bd("100"), bd("120"))).isEqualByComparingTo("20");
    }
}
