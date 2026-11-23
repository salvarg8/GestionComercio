package org.gestionComercio.licencia;

import org.gestionComercio.enums.PermisoCodigo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.gestionComercio.licencia.LicenciasDePrueba.EQUIPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermisosLicenciaTest {

    private static final Set<PermisoCodigo> TODOS = EnumSet.allOf(PermisoCodigo.class);

    private static final LocalDate HOY = LocalDate.of(2026, 11, 23);

    @Test
    @DisplayName("con la licencia vigente se conservan todos los permisos")
    void vigente() {

        EstadoLicencia estado = estado(SituacionLicencia.ACTIVA,
                LicenciasDePrueba.licencia(null, EQUIPO));

        assertEquals(TODOS, PermisosLicencia.habilitados(TODOS, estado));
    }

    @Test
    @DisplayName("en modo sólo consulta quedan ver, reportes, crear copias de seguridad y administrar la licencia")
    void soloConsulta() {

        Set<PermisoCodigo> habilitados = PermisosLicencia.habilitados(TODOS,
                estado(SituacionLicencia.PRUEBA_VENCIDA, null));

        Set<PermisoCodigo> esperados = Arrays.stream(PermisoCodigo.values())
                .filter(p -> p.name().endsWith("_VER") || p.name().contains("_VER_")
                        || p.name().startsWith("REPORTE_"))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermisoCodigo.class)));
        esperados.add(PermisoCodigo.BACKUP_CREAR);
        esperados.add(PermisoCodigo.LICENCIA_ADMINISTRAR);

        assertEquals(esperados, habilitados);

        // Las operaciones que modifican datos no quedan.
        for (PermisoCodigo operacion : EnumSet.of(PermisoCodigo.VENTA_CREAR, PermisoCodigo.VENTA_ANULAR,
                PermisoCodigo.CAJA_ABRIR, PermisoCodigo.COMPRA_CREAR, PermisoCodigo.PRODUCTO_EDITAR,
                PermisoCodigo.INVENTARIO_AJUSTAR, PermisoCodigo.BACKUP_RESTAURAR,
                PermisoCodigo.FACTURACION_REINTENTAR, PermisoCodigo.USUARIO_CREAR)) {
            assertFalse(habilitados.contains(operacion), operacion.name());
        }

        // Se puede seguir consultando y exportando.
        assertTrue(habilitados.containsAll(EnumSet.of(PermisoCodigo.VENTA_VER, PermisoCodigo.CLIENTE_VER,
                PermisoCodigo.REPORTE_VENTAS, PermisoCodigo.CAJA_VER_CERRADAS_TODAS)));
    }

    @Test
    @DisplayName("sin el módulo de facturación electrónica se quitan sus permisos")
    void sinModulo() {

        EstadoLicencia estado = estado(SituacionLicencia.ACTIVA, LicenciasDePrueba.licencia(
                LocalDateTime.of(2026, 1, 1, 0, 0), null, EnumSet.noneOf(ModuloLicencia.class), EQUIPO));

        Set<PermisoCodigo> habilitados = PermisosLicencia.habilitados(TODOS, estado);

        assertFalse(habilitados.contains(PermisoCodigo.FACTURACION_CONFIGURAR));
        assertFalse(habilitados.contains(PermisoCodigo.FACTURACION_REINTENTAR));
        assertTrue(habilitados.contains(PermisoCodigo.VENTA_CREAR));
    }

    @Test
    @DisplayName("no agrega permisos que el usuario no tiene")
    void noAgrega() {

        Set<PermisoCodigo> vendedor = EnumSet.of(PermisoCodigo.VENTA_CREAR, PermisoCodigo.CAJA_VER);

        assertEquals(EnumSet.of(PermisoCodigo.CAJA_VER),
                PermisosLicencia.habilitados(vendedor, estado(SituacionLicencia.VENCIDA,
                        LicenciasDePrueba.licencia(HOY.minusDays(1), EQUIPO))));
    }

    private static EstadoLicencia estado(SituacionLicencia situacion, Licencia licencia) {
        return new EstadoLicencia(situacion, licencia, EQUIPO, null, null, HOY, null);
    }

}
