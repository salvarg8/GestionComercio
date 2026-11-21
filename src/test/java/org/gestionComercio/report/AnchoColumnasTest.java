package org.gestionComercio.report;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.gestionComercio.dto.reporte.ColumnaReporte;
import org.gestionComercio.dto.reporte.FormatoDato;
import org.gestionComercio.dto.reporte.Reporte;
import org.gestionComercio.enums.TipoReporte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.gestionComercio.dto.reporte.Reporte.fila;

/** Ancho de las columnas de los reportes según su contenido. */
class AnchoColumnasTest {

    private static float suma(float[] anchos) {
        float total = 0;
        for (float ancho : anchos) {
            total += ancho;
        }
        return total;
    }

    @Test
    @DisplayName("Si sobra lugar, se lo llevan las columnas de texto y las numéricas conservan su ancho")
    void sobrante() {

        float[] anchos = ReportePdf.ajustarAnchos(
                new float[] {100, 50, 60}, new boolean[] {true, false, false}, 300);

        assertThat(anchos).containsExactly(190, 50, 60);
    }

    @Test
    @DisplayName("Si falta lugar, se achica la columna de texto más larga y las cortas quedan completas")
    void faltante() {

        float[] anchos = ReportePdf.ajustarAnchos(
                new float[] {60, 400, 80, 100}, new boolean[] {true, true, false, false}, 400);

        assertThat(anchos[0]).isEqualTo(60);
        assertThat(anchos[1]).isEqualTo(160);
        assertThat(anchos[2]).isEqualTo(80);
        assertThat(anchos[3]).isEqualTo(100);
    }

    @Test
    @DisplayName("Varias columnas de texto largas quedan con el mismo ancho")
    void topeComun() {

        float[] anchos = ReportePdf.ajustarAnchos(
                new float[] {300, 500, 50}, new boolean[] {true, true, false}, 450);

        assertThat(anchos).containsExactly(200, 200, 50);
    }

    @Test
    @DisplayName("Si ni achicando el texto entra, se achican todas en proporción")
    void proporcional() {

        float[] anchos = ReportePdf.ajustarAnchos(
                new float[] {200, 200}, new boolean[] {false, false}, 100);

        assertThat(anchos).containsExactly(50, 50);
        assertThat(suma(anchos)).isCloseTo(100, within(0.01f));
    }

    private static Reporte reporteProductos() {

        return new Reporte(TipoReporte.VALORIZACION_INVENTARIO, "Empresa", "Situación al 21/11/2026",
                LocalDateTime.of(2026, 11, 21, 14, 30),
                List.of(ColumnaReporte.texto("Código"),
                        ColumnaReporte.texto("Producto"),
                        ColumnaReporte.de("Valor", FormatoDato.IMPORTE)),
                List.of(fila("A1", "Yerba mate suave 1 kg", new BigDecimal("10")),
                        fila("B2", "Aceite", new BigDecimal("1234567.50"))),
                fila("Total", null, new BigDecimal("12345677.50")));
    }

    @Test
    @DisplayName("El texto más largo de cada columna incluye los totales")
    void textoMasLargo() {

        Reporte reporte = reporteProductos();

        assertThat(reporte.textoMasLargo(0)).isEqualTo("Total");
        assertThat(reporte.textoMasLargo(1)).isEqualTo("Yerba mate suave 1 kg");
        assertThat(reporte.textoMasLargo(2)).isEqualTo(FormatoDato.IMPORTE.formatear(new BigDecimal("12345677.50")));
    }

    @Test
    @DisplayName("En Excel cada columna toma el ancho de su contenido, sin contar el encabezado del reporte")
    void excel() throws Exception {

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(ReporteExcel.generar(reporteProductos())))) {

            Sheet hoja = libro.getSheetAt(0);
            int codigo = hoja.getColumnWidth(0) / 256;
            int producto = hoja.getColumnWidth(1) / 256;

            // La columna A no se ensancha por el título "Valorización del inventario".
            assertThat(codigo).isLessThan("Valorización del inventario".length());
            assertThat(producto).isGreaterThanOrEqualTo("Yerba mate suave 1 kg".length());
        }
    }
}
