package org.gestionComercio.controller.reporte;

import org.gestionComercio.dto.reporte.ColumnaReporte;
import org.gestionComercio.dto.reporte.Reporte;
import org.gestionComercio.enums.TipoReporte;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportacionReporteTest {

    @Test
    @DisplayName("El nombre de archivo propuesto es el título sin acentos ni espacios y la fecha de generación")
    void nombreArchivo() {
        Reporte reporte = new Reporte(TipoReporte.VALORIZACION_INVENTARIO, "", "",
                LocalDateTime.of(2026, 11, 21, 14, 30),
                List.of(ColumnaReporte.texto("A")), List.of(), null);

        assertThat(ExportacionReporte.nombreArchivo(reporte))
                .isEqualTo("Valorizacion-del-inventario_2026-11-21_1430");
    }
}
