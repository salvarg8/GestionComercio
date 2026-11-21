package org.gestionComercio.report;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.gestionComercio.dto.reporte.ColumnaReporte;
import org.gestionComercio.dto.reporte.FormatoDato;
import org.gestionComercio.dto.reporte.Reporte;
import org.gestionComercio.util.DateUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Un {@link Reporte} en una planilla de Excel (.xlsx): encabezado con el
 * título, la empresa, el período y la fecha de generación; la tabla con
 * filtro automático y la fila de totales. Los importes, cantidades,
 * porcentajes y fechas se guardan como números y fechas de Excel (no como
 * texto), para poder sumarlos u ordenarlos en la planilla.
 */
public final class ReporteExcel {

    /** Fila (base 0) con los títulos de las columnas. */
    static final int FILA_TITULOS = 5;

    /** Ancho máximo de una columna, en caracteres. */
    private static final int ANCHO_MAXIMO = 60;

    private final Reporte reporte;
    private final XSSFWorkbook libro;
    private final Map<FormatoDato, CellStyle> estilos = new EnumMap<>(FormatoDato.class);
    private final Map<FormatoDato, CellStyle> estilosTotal = new EnumMap<>(FormatoDato.class);

    private ReporteExcel(Reporte reporte, XSSFWorkbook libro) {
        this.reporte = reporte;
        this.libro = libro;
    }

    /** @return el archivo .xlsx del reporte */
    public static byte[] generar(Reporte reporte) {

        try (XSSFWorkbook libro = new XSSFWorkbook()) {

            new ReporteExcel(reporte, libro).completar();

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();

        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el reporte en Excel.", e);
        }
    }

    private void completar() {

        crearEstilos();

        Sheet hoja = libro.createSheet(WorkbookUtil.createSafeSheetName(reporte.titulo()));
        List<ColumnaReporte> columnas = reporte.columnas();

        texto(hoja, 0, reporte.titulo(), estiloTitulo());
        texto(hoja, 1, reporte.empresa(), null);
        texto(hoja, 2, reporte.filtros(), null);
        texto(hoja, 3, "Generado el " + DateUtils.format(reporte.generado()), null);

        Row titulos = hoja.createRow(FILA_TITULOS);
        CellStyle estiloEncabezado = estiloEncabezado();

        for (int i = 0; i < columnas.size(); i++) {
            Cell celda = titulos.createCell(i);
            celda.setCellValue(columnas.get(i).titulo());
            celda.setCellStyle(estiloEncabezado);
        }

        int numero = FILA_TITULOS + 1;

        for (List<Object> fila : reporte.filas()) {
            escribirFila(hoja.createRow(numero++), fila, estilos);
        }

        if (!reporte.filas().isEmpty()) {
            hoja.setAutoFilter(new CellRangeAddress(
                    FILA_TITULOS, numero - 1, 0, columnas.size() - 1));
        }

        if (reporte.tieneTotales()) {
            escribirFila(hoja.createRow(numero), reporte.totales(), estilosTotal);
        }

        hoja.createFreezePane(0, FILA_TITULOS + 1);

        // El ancho sale de la tabla y no del encabezado del reporte, que ocupa la columna A.
        for (int i = 0; i < columnas.size(); i++) {
            hoja.setColumnWidth(i, anchoColumna(i) * 256);
        }
    }

    /**
     * Ancho de la columna en caracteres: el de su texto más largo (el título va
     * en negrita, que ocupa algo más), con un margen y sin pasar del máximo.
     */
    private int anchoColumna(int columna) {

        int titulo = (int) Math.ceil(reporte.columnas().get(columna).titulo().length() * 1.15);
        int contenido = reporte.textoMasLargo(columna).length();

        return Math.min(Math.max(titulo, contenido) + 3, ANCHO_MAXIMO);
    }

    private void escribirFila(Row fila, List<Object> celdas, Map<FormatoDato, CellStyle> estilosFila) {

        for (int i = 0; i < celdas.size(); i++) {

            FormatoDato formato = reporte.columnas().get(i).formato();
            Object valor = celdas.get(i);
            Cell celda = fila.createCell(i);

            // Una leyenda en una columna numérica se guarda como texto, con el estilo de texto.
            celda.setCellStyle(estilosFila.get(valor instanceof String ? FormatoDato.TEXTO : formato));

            if (valor == null) {
                continue;
            }

            switch (valor) {
                case BigDecimal numero -> celda.setCellValue(numero.doubleValue());
                case Long numero -> celda.setCellValue(numero);
                case Integer numero -> celda.setCellValue(numero);
                case LocalDateTime fecha -> celda.setCellValue(fecha);
                case LocalDate fecha -> celda.setCellValue(fecha);
                default -> celda.setCellValue(valor.toString());
            }
        }
    }

    private static void texto(Sheet hoja, int fila, String texto, CellStyle estilo) {

        Cell celda = hoja.createRow(fila).createCell(0);
        celda.setCellValue(texto == null ? "" : texto);

        if (estilo != null) {
            celda.setCellStyle(estilo);
        }
    }

    // ------------------------------------------------------------------
    // Estilos
    // ------------------------------------------------------------------

    private void crearEstilos() {

        Font negrita = libro.createFont();
        negrita.setBold(true);

        for (FormatoDato formato : FormatoDato.values()) {
            estilos.put(formato, estiloDato(formato, null));
            estilosTotal.put(formato, estiloDato(formato, negrita));
        }
    }

    private CellStyle estiloDato(FormatoDato formato, Font fuente) {

        CellStyle estilo = libro.createCellStyle();
        DataFormat formatos = libro.createDataFormat();

        String patron = switch (formato) {
            case TEXTO -> null;
            case ENTERO -> "#,##0";
            case CANTIDAD -> "#,##0.###";
            case IMPORTE -> "\"$\" #,##0.00";
            case PORCENTAJE -> "0.00%";
            case FECHA -> "dd/mm/yyyy";
            case FECHA_HORA -> "dd/mm/yyyy hh:mm";
        };

        if (patron != null) {
            estilo.setDataFormat(formatos.getFormat(patron));
        }

        if (formato.esNumerico()) {
            estilo.setAlignment(HorizontalAlignment.RIGHT);
        }

        if (fuente != null) {
            estilo.setFont(fuente);
            estilo.setBorderTop(BorderStyle.THIN);
        }

        return estilo;
    }

    private CellStyle estiloTitulo() {

        Font fuente = libro.createFont();
        fuente.setBold(true);
        fuente.setFontHeightInPoints((short) 14);

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        return estilo;
    }

    private CellStyle estiloEncabezado() {

        Font fuente = libro.createFont();
        fuente.setBold(true);

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setBorderBottom(BorderStyle.THIN);
        return estilo;
    }
}
