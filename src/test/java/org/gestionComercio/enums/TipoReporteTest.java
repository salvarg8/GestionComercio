package org.gestionComercio.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TipoReporteTest {

    @Test
    @DisplayName("Todos los reportes exigen un permiso REPORTE_*")
    void permisos() {
        for (TipoReporte tipo : TipoReporte.values()) {
            assertThat(tipo.getPermiso().name()).startsWith("REPORTE_");
        }
    }

    @Test
    @DisplayName("Se ofrecen sólo los reportes cuyo permiso tiene el usuario")
    void permitidos() {
        Set<PermisoCodigo> permisos = Set.of(PermisoCodigo.REPORTE_COMPRAS, PermisoCodigo.REPORTE_CAJA);

        assertThat(TipoReporte.permitidos(permisos::contains))
                .containsExactly(TipoReporte.COMPRAS_POR_PROVEEDOR, TipoReporte.MOVIMIENTOS_CAJA);
    }
}
