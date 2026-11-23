package org.gestionComercio.service.impl;

import org.gestionComercio.entity.RegistroLicencia;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.licencia.EstadoLicencia;
import org.gestionComercio.licencia.Licencia;
import org.gestionComercio.licencia.LicenciaInvalidaException;
import org.gestionComercio.licencia.LicenciasDePrueba;
import org.gestionComercio.licencia.ModuloLicencia;
import org.gestionComercio.licencia.SituacionLicencia;
import org.gestionComercio.repository.RegistroLicenciaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Optional;

import static org.gestionComercio.licencia.LicenciasDePrueba.EQUIPO;
import static org.gestionComercio.licencia.LicenciasDePrueba.OTRO_EQUIPO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LicenciaServiceImplTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 23);

    private RegistroLicenciaRepository repository;
    private SesionUsuario sesion;
    private RegistroLicencia registro;

    @BeforeEach
    void setUp() {
        repository = mock(RegistroLicenciaRepository.class);
        sesion = mock(SesionUsuario.class);
        registro = RegistroLicencia.builder()
                .id(RegistroLicencia.ID)
                .inicioPrueba(HOY)
                .ultimaFecha(HOY)
                .build();
        when(repository.findById(RegistroLicencia.ID)).thenReturn(Optional.of(registro));
    }

    private LicenciaServiceImpl servicio(LocalDate hoy) {
        return servicio(hoy, EQUIPO);
    }

    private LicenciaServiceImpl servicio(LocalDate hoy, String equipo) {
        Clock reloj = Clock.fixed(hoy.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault());
        return new LicenciaServiceImpl(repository, LicenciasDePrueba.verificador(), () -> equipo, sesion, reloj);
    }

    private void activa(Licencia licencia) {
        registro.setClave(LicenciasDePrueba.firmar(licencia));
    }

    // ------------------------------------------------------------------
    // Período de prueba
    // ------------------------------------------------------------------

    @Test
    @DisplayName("recién instalado: período de prueba con todos los módulos")
    void pruebaVigente() {

        EstadoLicencia estado = servicio(HOY).estado();

        assertEquals(SituacionLicencia.PRUEBA, estado.situacion());
        assertEquals(30, estado.diasRestantes());
        assertTrue(estado.permiteOperar());
        assertTrue(estado.tieneModulo(ModuloLicencia.FACTURACION_ELECTRONICA));
    }

    @Test
    @DisplayName("el último día de prueba todavía se puede operar; al día siguiente, sólo consulta")
    void finDeLaPrueba() {

        EstadoLicencia ultimoDia = servicio(HOY.plusDays(29)).estado();
        assertEquals(SituacionLicencia.PRUEBA, ultimoDia.situacion());
        assertEquals(1, ultimoDia.diasRestantes());

        registro.setUltimaFecha(HOY.plusDays(29));
        EstadoLicencia vencida = servicio(HOY.plusDays(30)).estado();
        assertEquals(SituacionLicencia.PRUEBA_VENCIDA, vencida.situacion());
        assertFalse(vencida.permiteOperar());
        assertFalse(vencida.tieneModulo(ModuloLicencia.FACTURACION_ELECTRONICA));
    }

    // ------------------------------------------------------------------
    // Licencia activada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("licencia de pago único en una PC incluida: activa")
    void perpetuaActiva() {

        activa(LicenciasDePrueba.licencia(null, OTRO_EQUIPO, EQUIPO));

        EstadoLicencia estado = servicio(HOY.plusYears(5)).estado();

        assertEquals(SituacionLicencia.ACTIVA, estado.situacion());
        assertFalse(estado.requiereAviso());
        assertTrue(estado.tieneModulo(ModuloLicencia.FACTURACION_ELECTRONICA));
    }

    @Test
    @DisplayName("sin el módulo en la licencia, no está habilitado")
    void sinModulo() {

        activa(LicenciasDePrueba.licencia(LocalDateTime.of(2026, 1, 1, 0, 0), null,
                EnumSet.noneOf(ModuloLicencia.class), EQUIPO));

        assertFalse(servicio(HOY).habilitado(ModuloLicencia.FACTURACION_ELECTRONICA));
    }

    @Test
    @DisplayName("abono: avisa desde 15 días antes, vale hasta el día de vencimiento y después queda en sólo consulta")
    void abono() {

        LocalDate vence = HOY.plusMonths(2);
        activa(LicenciasDePrueba.licencia(vence, EQUIPO));

        assertEquals(SituacionLicencia.ACTIVA, servicio(vence.minusDays(16)).estado().situacion());

        EstadoLicencia porVencer = servicio(vence.minusDays(15)).estado();
        assertEquals(SituacionLicencia.POR_VENCER, porVencer.situacion());
        assertEquals(15, porVencer.diasRestantes());
        assertTrue(porVencer.permiteOperar());

        EstadoLicencia ultimoDia = servicio(vence).estado();
        assertEquals(SituacionLicencia.POR_VENCER, ultimoDia.situacion());
        assertEquals(0, ultimoDia.diasRestantes());

        EstadoLicencia vencida = servicio(vence.plusDays(1)).estado();
        assertEquals(SituacionLicencia.VENCIDA, vencida.situacion());
        assertFalse(vencida.permiteOperar());
    }

    @Test
    @DisplayName("la licencia de un comercio no funciona en una PC que no incluye")
    void otraPc() {

        activa(LicenciasDePrueba.licencia(null, EQUIPO));

        EstadoLicencia estado = servicio(HOY, OTRO_EQUIPO).estado();

        assertEquals(SituacionLicencia.EQUIPO_NO_HABILITADO, estado.situacion());
        assertFalse(estado.permiteOperar());
        assertTrue(estado.mensaje().contains(OTRO_EQUIPO));
    }

    @Test
    @DisplayName("una licencia modificada en la base deja el sistema en sólo consulta")
    void claveModificada() {

        activa(LicenciasDePrueba.licencia(null, EQUIPO));
        String clave = registro.getClave();
        int posicion = clave.length() - 5;
        char cambiado = clave.charAt(posicion) == 'A' ? 'B' : 'A';
        registro.setClave(clave.substring(0, posicion) + cambiado + clave.substring(posicion + 1));

        EstadoLicencia estado = servicio(HOY).estado();

        assertEquals(SituacionLicencia.INVALIDA, estado.situacion());
        assertFalse(estado.permiteOperar());
    }

    // ------------------------------------------------------------------
    // Reloj
    // ------------------------------------------------------------------

    @Test
    @DisplayName("atrasar el reloj para estirar un abono deja el sistema en sólo consulta")
    void relojAtrasado() {

        activa(LicenciasDePrueba.licencia(HOY.plusDays(10), EQUIPO));
        registro.setUltimaFecha(HOY.plusDays(40));

        EstadoLicencia estado = servicio(HOY).estado();

        assertEquals(SituacionLicencia.RELOJ_ATRASADO, estado.situacion());
        assertFalse(estado.permiteOperar());
    }

    @Test
    @DisplayName("un día de diferencia entre los relojes de las PC se tolera")
    void toleranciaDeUnDia() {

        registro.setUltimaFecha(HOY.plusDays(1));

        assertEquals(SituacionLicencia.PRUEBA, servicio(HOY).estado().situacion());
    }

    @Test
    @DisplayName("con una licencia sin vencimiento la fecha de la PC no importa")
    void relojConLicenciaPerpetua() {

        activa(LicenciasDePrueba.licencia(null, EQUIPO));
        registro.setUltimaFecha(HOY.plusYears(1));

        assertEquals(SituacionLicencia.ACTIVA, servicio(HOY).estado().situacion());
    }

    @Test
    @DisplayName("al iniciar sesión se registra la fecha de uso")
    void registraUso() {

        servicio(HOY.plusDays(3)).verificarAlIniciarSesion();

        verify(repository).registrarUso(RegistroLicencia.ID, HOY.plusDays(3));
    }

    // ------------------------------------------------------------------
    // Activación
    // ------------------------------------------------------------------

    @Test
    @DisplayName("activar guarda la licencia y queda activa")
    void activar() {

        String clave = LicenciasDePrueba.firmar(LicenciasDePrueba.licencia(HOY.plusYears(1), EQUIPO));

        EstadoLicencia estado = servicio(HOY).activar("# encabezado\n" + clave + "\n");

        assertEquals(SituacionLicencia.ACTIVA, estado.situacion());
        assertEquals(clave, registro.getClave());
        verify(repository).save(registro);
    }

    @Test
    @DisplayName("activar requiere LICENCIA_ADMINISTRAR")
    void activarSinPermiso() {

        doThrow(new AuthorizationException("No tiene permiso"))
                .when(sesion).requerirPermiso(PermisoCodigo.LICENCIA_ADMINISTRAR);
        String clave = LicenciasDePrueba.firmar(LicenciasDePrueba.licencia(null, EQUIPO));
        LicenciaServiceImpl servicio = servicio(HOY);

        assertThrows(AuthorizationException.class, () -> servicio.activar(clave));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("no se activa una licencia que no incluye esta PC")
    void activarOtraPc() {

        String clave = LicenciasDePrueba.firmar(LicenciasDePrueba.licencia(null, OTRO_EQUIPO));
        LicenciaServiceImpl servicio = servicio(HOY);

        BusinessException e = assertThrows(BusinessException.class, () -> servicio.activar(clave));
        assertTrue(e.getMessage().contains(EQUIPO));
        assertNull(registro.getClave());
    }

    @Test
    @DisplayName("no se activa una licencia modificada ni una vencida")
    void activarInvalida() {

        LicenciaServiceImpl servicio = servicio(HOY);
        String vencida = LicenciasDePrueba.firmar(LicenciasDePrueba.licencia(HOY.minusDays(1), EQUIPO));

        assertThrows(LicenciaInvalidaException.class, () -> servicio.activar("GC1.abc.def"));
        assertThrows(BusinessException.class, () -> servicio.activar(vencida));
        assertNull(registro.getClave());
    }

    @Test
    @DisplayName("no se reemplaza la licencia activa por una emitida antes")
    void activarAnterior() {

        LocalDate vence = HOY.plusYears(1);
        activa(LicenciasDePrueba.licencia(LocalDateTime.of(2026, 6, 1, 0, 0), vence,
                EnumSet.noneOf(ModuloLicencia.class), EQUIPO));
        String anterior = LicenciasDePrueba.firmar(LicenciasDePrueba.licencia(LocalDateTime.of(2026, 1, 1, 0, 0),
                vence, EnumSet.noneOf(ModuloLicencia.class), EQUIPO));
        LicenciaServiceImpl servicio = servicio(HOY);

        assertThrows(BusinessException.class, () -> servicio.activar(anterior));
    }

    @Test
    @DisplayName("una licencia nueva reinicia la última fecha registrada (PC que tuvo la fecha adelantada)")
    void licenciaNuevaReiniciaFecha() {

        registro.setUltimaFecha(HOY.plusYears(3));
        String nueva = LicenciasDePrueba.firmar(LicenciasDePrueba.licencia(HOY.plusYears(1), EQUIPO));

        EstadoLicencia estado = servicio(HOY).activar(nueva);

        assertEquals(HOY, registro.getUltimaFecha());
        assertEquals(SituacionLicencia.ACTIVA, estado.situacion());
    }

}
