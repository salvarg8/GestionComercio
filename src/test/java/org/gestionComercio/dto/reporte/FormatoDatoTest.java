package org.gestionComercio.dto.reporte;

import org.gestionComercio.enums.TipoReporte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormatoDatoTest {

    @Test
    @DisplayName("Cada formato muestra su valor con el formato argentino")
    void formatear() {
        assertThat(FormatoDato.IMPORTE.formatear(new BigDecimal("1234.5"))).contains("1.234,50");
        assertThat(FormatoDato.CANTIDAD.formatear(new BigDecimal("1.500"))).isEqualTo("1,5");
        assertThat(FormatoDato.ENTERO.formatear(12L)).isEqualTo("12");
        assertThat(FormatoDato.PORCENTAJE.formatear(new BigDecimal("0.8333"))).contains("83,33").contains("%");
        assertThat(FormatoDato.FECHA.formatear(LocalDate.of(2026, 11, 2))).isEqualTo("02/11/2026");
        assertThat(FormatoDato.FECHA_HORA.formatear(LocalDateTime.of(2026, 11, 2, 9, 5)))
                .isEqualTo("02/11/2026 09:05");
    }

    @Test
    @DisplayName("Null se muestra vacío y un texto en una columna numérica, tal cual")
    void vacioYLeyendas() {
        assertThat(FormatoDato.IMPORTE.formatear(null)).isEmpty();
        assertThat(FormatoDato.IMPORTE.formatear("Total")).isEqualTo("Total");
    }

    @Test
    @DisplayName("El reporte rechaza filas con una cantidad de celdas distinta a la de columnas")
    void anchoDeFilas() {
        List<ColumnaReporte> columnas = List.of(ColumnaReporte.texto("A"), ColumnaReporte.texto("B"));

        assertThatThrownBy(() -> new Reporte(TipoReporte.STOCK_BAJO, "", "", null,
                columnas, List.of(Reporte.fila("solo una")), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
