package org.gestionComercio.report;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Row;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.gestionComercio.dto.reporte.Reporte.fila;

/** Exportación de un reporte a PDF y a Excel. */
class ReportesExportacionTest {

    private static final LocalDateTime GENERADO = LocalDateTime.of(2026, 11, 21, 14, 30);

    private static Reporte reporte(int cantidadFilas) {

        List<List<Object>> filas = new ArrayList<>();

        for (int i = 1; i <= cantidadFilas; i++) {
            filas.add(fila(LocalDate.of(2026, 11, 1).plusDays(i % 28), (long) i,
                    new BigDecimal("1234.50"), null, new BigDecimal("1234.50")));
        }

        return new Reporte(TipoReporte.VENTAS_POR_PERIODO, "Almacén Ñandú", "Del 01/11/2026 al 21/11/2026",
                GENERADO,
                List.of(ColumnaReporte.de("Fecha", FormatoDato.FECHA),
                        ColumnaReporte.de("Ventas", FormatoDato.ENTERO),
                        ColumnaReporte.de("Subtotal", FormatoDato.IMPORTE),
                        ColumnaReporte.de("Descuentos", FormatoDato.IMPORTE),
                        ColumnaReporte.de("Total", FormatoDato.IMPORTE)),
                filas,
                fila("Total", (long) cantidadFilas, null, null,
                        new BigDecimal("1234.50").multiply(BigDecimal.valueOf(cantidadFilas))));
    }

    @Test
    @DisplayName("El PDF tiene el encabezado, las filas formateadas y la fila de totales")
    void pdf() throws Exception {

        String texto = textoPdf(ReportePdf.generar(reporte(2)));

        assertThat(texto)
                .contains("Almacén Ñandú")
                .contains("Ventas por período")
                .contains("Del 01/11/2026 al 21/11/2026")
                .contains("Generado el 21/11/2026 14:30")
                .contains("02/11/2026")
                .contains("1.234,50")
                .contains("Total")
                .contains("2.469,00")
                .contains("Página 1 de 1");
    }

    @Test
    @DisplayName("Un reporte largo continúa en varias páginas")
    void pdfVariasPaginas() throws Exception {

        byte[] pdf = ReportePdf.generar(reporte(120));

        try (PDDocument documento = Loader.loadPDF(pdf)) {
            assertThat(documento.getNumberOfPages()).isGreaterThan(1);
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto).contains("Página 1 de " + documento.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("Un reporte sin filas genera el PDF con el aviso de que no hay datos")
    void pdfVacio() throws Exception {

        Reporte vacio = new Reporte(TipoReporte.STOCK_BAJO, "", "Situación al 21/11/2026 14:30", GENERADO,
                List.of(ColumnaReporte.texto("Producto")), List.of(), null);

        assertThat(textoPdf(ReportePdf.generar(vacio))).contains("No hay datos");
    }

    @Test
    @DisplayName("El Excel guarda importes, cantidades y fechas como números, con la fila de totales")
    void excel() throws Exception {

        byte[] xlsx = ReporteExcel.generar(reporte(2));

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {

            Sheet hoja = libro.getSheetAt(0);

            assertThat(hoja.getSheetName()).isEqualTo("Ventas por período");
            assertThat(hoja.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Ventas por período");
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Almacén Ñandú");
            assertThat(hoja.getRow(2).getCell(0).getStringCellValue()).isEqualTo("Del 01/11/2026 al 21/11/2026");

            Row titulos = hoja.getRow(ReporteExcel.FILA_TITULOS);
            assertThat(titulos.getCell(0).getStringCellValue()).isEqualTo("Fecha");
            assertThat(titulos.getCell(4).getStringCellValue()).isEqualTo("Total");

            Row primera = hoja.getRow(ReporteExcel.FILA_TITULOS + 1);
            assertThat(primera.getCell(0).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 11, 2));
            assertThat(primera.getCell(1).getNumericCellValue()).isEqualTo(1);
            assertThat(primera.getCell(2).getNumericCellValue()).isEqualTo(1234.5);

            Row totales = hoja.getRow(ReporteExcel.FILA_TITULOS + 3);
            assertThat(totales.getCell(0).getStringCellValue()).isEqualTo("Total");
            assertThat(totales.getCell(1).getNumericCellValue()).isEqualTo(2);
            assertThat(totales.getCell(4).getNumericCellValue()).isEqualTo(2469.0);
        }
    }

    private static String textoPdf(byte[] pdf) throws Exception {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }
}
