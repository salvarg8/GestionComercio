package org.gestionComercio.service.impl;

import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.dto.caja.EstadoEfectivoCaja;
import org.gestionComercio.dto.caja.MovimientoCajaDto;
import org.gestionComercio.dto.caja.ResponsableCajaDto;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.entity.Permiso;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.FiltroCaja;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.EstadoUsuario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.exception.AuthenticationException;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.mapper.CajaMapper;
import org.gestionComercio.mapper.MovimientoCajaMapper;
import org.gestionComercio.repository.CajaRepository;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.UsuarioRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.AuthenticationService;
import org.gestionComercio.util.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CajaServiceImplTest {

    private static final Long USUARIO_ID = 7L;
    private static final Long CAJA_ID = 1L;

    @Mock
    private CajaRepository cajaRepository;

    @Mock
    private MovimientoCajaRepository movimientoCajaRepository;

    @Mock
    private CajaMapper cajaMapper;

    @Mock
    private MovimientoCajaMapper movimientoCajaMapper;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private ControlEfectivoCaja controlEfectivoCaja;

    @Mock
    private AuthenticationService authenticationService;

    private SesionUsuario sesionUsuario;

    private CajaServiceImpl cajaService;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        loguearCon(EnumSet.allOf(PermisoCodigo.class));

        cajaService = new CajaServiceImpl(
                cajaRepository, movimientoCajaRepository, cajaMapper, movimientoCajaMapper, sesionUsuario,
                usuarioRepository, empresaRepository, controlEfectivoCaja, authenticationService);

        lenient().when(cajaRepository.save(any())).thenAnswer(inv -> {
            Caja caja = inv.getArgument(0);
            if (caja.getId() == null) {
                caja.setId(CAJA_ID);
            }
            return caja;
        });
        lenient().when(movimientoCajaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cajaMapper.toDto(any())).thenAnswer(inv -> {
            Caja caja = inv.getArgument(0);
            return CajaDto.builder()
                    .id(caja.getId())
                    .estadoCaja(caja.getEstadoCaja())
                    .saldoInicial(caja.getSaldoInicial())
                    .saldoEsperado(caja.getSaldoEsperado())
                    .saldoFinal(caja.getSaldoFinal())
                    .diferencia(caja.getDiferencia())
                    .fechaCierre(caja.getFechaCierre())
                    .build();
        });
        lenient().when(movimientoCajaMapper.toDto(any())).thenAnswer(inv -> {
            MovimientoCaja m = inv.getArgument(0);
            return MovimientoCajaDto.builder().tipo(m.getTipo()).importe(m.getImporte())
                    .descripcion(m.getDescripcion()).build();
        });
    }

    private void loguearCon(EnumSet<PermisoCodigo> permisos) {
        Usuario usuario = new Usuario();
        usuario.setId(USUARIO_ID);
        sesionUsuario.iniciarSesion(usuario, permisos);
    }

    private void loguearSin(PermisoCodigo permiso) {
        EnumSet<PermisoCodigo> permisos = EnumSet.allOf(PermisoCodigo.class);
        permisos.remove(permiso);
        loguearCon(permisos);
    }

    private Caja cajaAbierta(String saldoInicial) {
        Caja caja = Caja.builder()
                .saldoInicial(new BigDecimal(saldoInicial))
                .estadoCaja(EstadoCaja.ABIERTA)
                .usuario(sesionUsuario.getUsuario())
                .build();
        caja.setId(CAJA_ID);
        return caja;
    }

    private MovimientoCaja movimiento(TipoMovimientoCaja tipo, String importe) {
        return MovimientoCaja.builder().tipo(tipo).importe(new BigDecimal(importe)).build();
    }

    /** Caja abierta del usuario con los movimientos indicados. */
    private Caja conCajaAbierta(String saldoInicial, MovimientoCaja... movimientos) {
        Caja caja = cajaAbierta(saldoInicial);
        List<MovimientoCaja> lista = new ArrayList<>();
        lista.add(movimiento(TipoMovimientoCaja.APERTURA, saldoInicial));
        lista.addAll(List.of(movimientos));
        lenient().when(cajaRepository.findFirstByUsuarioIdAndEstadoCajaOrderByFechaAperturaDesc(
                USUARIO_ID, EstadoCaja.ABIERTA)).thenReturn(Optional.of(caja));
        lenient().when(movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(CAJA_ID)).thenReturn(lista);
        return caja;
    }

    private void sinCajaAbierta() {
        when(cajaRepository.findFirstByUsuarioIdAndEstadoCajaOrderByFechaAperturaDesc(
                USUARIO_ID, EstadoCaja.ABIERTA)).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------
    // Apertura
    // ------------------------------------------------------------------

    @Test
    @DisplayName("abrir crea la caja abierta y el movimiento de apertura con el saldo inicial")
    void abrirCreaCajaYMovimientoApertura() {

        CajaDto dto = cajaService.abrir(new BigDecimal("1500"));

        ArgumentCaptor<Caja> caja = ArgumentCaptor.forClass(Caja.class);
        verify(cajaRepository).save(caja.capture());
        assertEquals(EstadoCaja.ABIERTA, caja.getValue().getEstadoCaja());
        assertEquals(new BigDecimal("1500.00"), caja.getValue().getSaldoInicial());
        assertSame(sesionUsuario.getUsuario(), caja.getValue().getUsuario());
        assertSame(sesionUsuario.getUsuario(), caja.getValue().getUsuarioApertura());
        assertNotNull(caja.getValue().getFechaApertura());

        ArgumentCaptor<MovimientoCaja> mov = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(mov.capture());
        assertEquals(TipoMovimientoCaja.APERTURA, mov.getValue().getTipo());
        assertEquals(new BigDecimal("1500.00"), mov.getValue().getImporte());
        assertSame(caja.getValue(), mov.getValue().getCaja());

        assertEquals(new BigDecimal("1500.00"), dto.getSaldoEsperado());
        assertEquals(new BigDecimal("0.00"), dto.getTotalIngresos());
        assertEquals(new BigDecimal("0.00"), dto.getTotalEgresos());
    }

    @Test
    @DisplayName("abrir admite saldo inicial cero")
    void abrirConSaldoCero() {

        CajaDto dto = cajaService.abrir(BigDecimal.ZERO);

        assertEquals(new BigDecimal("0.00"), dto.getSaldoEsperado());
    }

    @Test
    @DisplayName("un usuario no puede tener más de una caja abierta")
    void abrirConCajaYaAbiertaFalla() {

        when(cajaRepository.existsByUsuarioIdAndEstadoCaja(USUARIO_ID, EstadoCaja.ABIERTA)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cajaService.abrir(new BigDecimal("100")));

        assertTrue(ex.getMessage().contains("Ya tiene una caja abierta"));
        verify(cajaRepository, never()).save(any());
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("abrir rechaza saldo inicial nulo, negativo o con más de dos decimales")
    void abrirValidaSaldoInicial() {

        assertThrows(BusinessException.class, () -> cajaService.abrir(null));
        assertThrows(BusinessException.class, () -> cajaService.abrir(new BigDecimal("-1")));
        assertThrows(BusinessException.class, () -> cajaService.abrir(new BigDecimal("10.555")));
        assertThrows(BusinessException.class, () -> cajaService.abrir(new BigDecimal("10000000000")));

        verify(cajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("abrir requiere CAJA_ABRIR")
    void abrirSinPermiso() {

        loguearSin(PermisoCodigo.CAJA_ABRIR);

        assertThrows(AuthorizationException.class, () -> cajaService.abrir(BigDecimal.TEN));
        verifyNoInteractions(cajaRepository, movimientoCajaRepository);
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("obtenerCajaAbierta calcula saldo inicial + ingresos − egresos")
    void obtenerCajaAbiertaCalculaSaldoEsperado() {

        conCajaAbierta("1000",
                movimiento(TipoMovimientoCaja.VENTA, "500"),
                movimiento(TipoMovimientoCaja.INGRESO_MANUAL, "250.50"),
                movimiento(TipoMovimientoCaja.GASTO, "100"),
                movimiento(TipoMovimientoCaja.RETIRO, "300"),
                movimiento(TipoMovimientoCaja.PAGO_PROVEEDOR, "50.25"));

        CajaDto dto = cajaService.obtenerCajaAbierta().orElseThrow();

        assertEquals(new BigDecimal("750.50"), dto.getTotalIngresos());
        assertEquals(new BigDecimal("450.25"), dto.getTotalEgresos());
        assertEquals(new BigDecimal("1300.25"), dto.getSaldoEsperado());
    }

    @Test
    @DisplayName("obtenerCajaAbierta devuelve vacío si el usuario no tiene caja abierta")
    void obtenerCajaAbiertaVacia() {

        sinCajaAbierta();

        assertTrue(cajaService.obtenerCajaAbierta().isEmpty());
    }

    @Test
    @DisplayName("obtenerCajaAbierta requiere CAJA_VER")
    void obtenerCajaAbiertaSinPermiso() {

        loguearSin(PermisoCodigo.CAJA_VER);

        assertThrows(AuthorizationException.class, () -> cajaService.obtenerCajaAbierta());
        verifyNoInteractions(cajaRepository);
    }

    @Test
    @DisplayName("listarCajasCerradas devuelve cada caja con sus totales y conserva el saldo esperado del cierre")
    void listarCajasCerradas() {

        Caja c1 = cajaAbierta("100");
        c1.setId(1L);
        c1.setEstadoCaja(EstadoCaja.CERRADA);
        c1.setSaldoEsperado(new BigDecimal("150.00"));
        Caja c2 = cajaAbierta("50");
        c2.setId(2L);
        c2.setEstadoCaja(EstadoCaja.CERRADA);
        c2.setSaldoEsperado(new BigDecimal("50.00"));

        MovimientoCaja venta = movimiento(TipoMovimientoCaja.VENTA, "80");
        venta.setCaja(c1);
        MovimientoCaja gasto = movimiento(TipoMovimientoCaja.GASTO, "30");
        gasto.setCaja(c1);

        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 18);
        when(cajaRepository.findCerradas(desde.atStartOfDay(), DateUtils.finDelDia(hasta)))
                .thenReturn(List.of(c1, c2));
        when(movimientoCajaRepository.findByCajaIdIn(List.of(1L, 2L))).thenReturn(List.of(venta, gasto));

        List<CajaDto> cajas = cajaService.listarCajasCerradas(desde, hasta);

        assertEquals(2, cajas.size());
        assertEquals(new BigDecimal("80.00"), cajas.get(0).getTotalIngresos());
        assertEquals(new BigDecimal("30.00"), cajas.get(0).getTotalEgresos());
        assertEquals(new BigDecimal("150.00"), cajas.get(0).getSaldoEsperado());
        assertEquals(new BigDecimal("0.00"), cajas.get(1).getTotalIngresos());
    }

    @Test
    @DisplayName("listarCajasCerradas sin fechas no filtra y sin resultados no consulta movimientos")
    void listarCajasCerradasSinFiltros() {

        when(cajaRepository.findCerradas(null, null)).thenReturn(List.of());

        assertTrue(cajaService.listarCajasCerradas(null, null).isEmpty());
        verifyNoInteractions(movimientoCajaRepository);
    }

    @Test
    @DisplayName("listarCajasCerradas rechaza un rango con desde posterior a hasta")
    void listarCajasCerradasRangoInvalido() {

        assertThrows(BusinessException.class, () -> cajaService.listarCajasCerradas(
                LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 1)));
        verifyNoInteractions(cajaRepository);
    }

    @Test
    @DisplayName("listarCajasCerradas requiere CAJA_VER")
    void listarCajasCerradasSinPermiso() {

        loguearSin(PermisoCodigo.CAJA_VER);

        assertThrows(AuthorizationException.class, () -> cajaService.listarCajasCerradas(null, null));
    }

    @Test
    @DisplayName("listarMovimientos devuelve los movimientos de la caja")
    void listarMovimientos() {

        List<MovimientoCaja> movimientos = List.of(movimiento(TipoMovimientoCaja.APERTURA, "10"));
        List<MovimientoCajaDto> dtos = List.of(new MovimientoCajaDto());
        when(cajaRepository.findById(CAJA_ID)).thenReturn(Optional.of(cajaAbierta("10")));
        when(movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(CAJA_ID)).thenReturn(movimientos);
        when(movimientoCajaMapper.toDtoList(movimientos)).thenReturn(dtos);

        assertSame(dtos, cajaService.listarMovimientos(CAJA_ID));
    }

    @Test
    @DisplayName("listarMovimientos falla si la caja no existe")
    void listarMovimientosCajaInexistente() {

        when(cajaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> cajaService.listarMovimientos(99L));
    }

    @Test
    @DisplayName("listarMovimientos requiere CAJA_VER")
    void listarMovimientosSinPermiso() {

        loguearSin(PermisoCodigo.CAJA_VER);

        assertThrows(AuthorizationException.class, () -> cajaService.listarMovimientos(CAJA_ID));
    }

    // ------------------------------------------------------------------
    // Movimientos
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = TipoMovimientoCaja.class, names = {"INGRESO_MANUAL", "EGRESO_MANUAL", "GASTO", "RETIRO"})
    @DisplayName("registrarMovimiento guarda ingresos, egresos, gastos y retiros manuales")
    void registrarMovimientoManual(TipoMovimientoCaja tipo) {

        Caja caja = conCajaAbierta("1000");

        MovimientoCajaDto dto = cajaService.registrarMovimiento(tipo, new BigDecimal("200"), "  Motivo  ");

        ArgumentCaptor<MovimientoCaja> mov = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(mov.capture());
        assertEquals(tipo, mov.getValue().getTipo());
        assertEquals(new BigDecimal("200.00"), mov.getValue().getImporte());
        assertEquals("Motivo", mov.getValue().getDescripcion());
        assertSame(caja, mov.getValue().getCaja());
        assertSame(sesionUsuario.getUsuario(), mov.getValue().getUsuario());
        assertNotNull(mov.getValue().getFecha());
        assertEquals(tipo, dto.getTipo());
    }

    @ParameterizedTest
    @EnumSource(value = TipoMovimientoCaja.class, names = {"APERTURA", "VENTA", "COBRO", "PAGO_PROVEEDOR", "CIERRE"})
    @DisplayName("registrarMovimiento rechaza los tipos que no son manuales")
    void registrarMovimientoNoManual(TipoMovimientoCaja tipo) {

        assertThrows(BusinessException.class,
                () -> cajaService.registrarMovimiento(tipo, BigDecimal.TEN, "x"));
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("no se registran movimientos sin caja abierta (por ejemplo, ya cerrada)")
    void registrarMovimientoSinCajaAbierta() {

        sinCajaAbierta();

        BusinessException ex = assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(
                TipoMovimientoCaja.INGRESO_MANUAL, BigDecimal.TEN, "x"));

        assertTrue(ex.getMessage().contains("No tiene una caja abierta"));
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("un egreso no puede superar el saldo disponible")
    void egresoSuperaSaldo() {

        conCajaAbierta("100", movimiento(TipoMovimientoCaja.GASTO, "30"));

        assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(
                TipoMovimientoCaja.RETIRO, new BigDecimal("70.01"), "Retiro"));
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("un egreso puede dejar la caja exactamente en cero")
    void egresoIgualAlSaldo() {

        conCajaAbierta("100", movimiento(TipoMovimientoCaja.GASTO, "30"));

        cajaService.registrarMovimiento(TipoMovimientoCaja.RETIRO, new BigDecimal("70"), "Retiro");

        verify(movimientoCajaRepository).save(any());
    }

    @Test
    @DisplayName("registrarMovimiento valida importe, descripción y tipo")
    void registrarMovimientoValida() {

        TipoMovimientoCaja t = TipoMovimientoCaja.INGRESO_MANUAL;

        assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(null, BigDecimal.TEN, "x"));
        assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(t, null, "x"));
        assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(t, BigDecimal.ZERO, "x"));
        assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(t, new BigDecimal("-5"), "x"));
        assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(t, new BigDecimal("1.001"), "x"));
        assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(t, BigDecimal.TEN, "   "));
        assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(t, BigDecimal.TEN, "a".repeat(256)));

        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("registrarMovimiento requiere CAJA_REGISTRAR_MOVIMIENTO")
    void registrarMovimientoSinPermiso() {

        loguearSin(PermisoCodigo.CAJA_REGISTRAR_MOVIMIENTO);

        assertThrows(AuthorizationException.class, () -> cajaService.registrarMovimiento(
                TipoMovimientoCaja.INGRESO_MANUAL, BigDecimal.TEN, "x"));
        verifyNoInteractions(cajaRepository, movimientoCajaRepository);
    }

    // ------------------------------------------------------------------
    // Control del efectivo y confirmación de retiros
    // ------------------------------------------------------------------

    private void retiroRequiereConfirmacion(boolean requiere) {
        Empresa empresa = Empresa.builder().retiroRequiereConfirmacion(requiere).build();
        lenient().when(empresaRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(empresa));
    }

    private Usuario encargado(String login, PermisoCodigo... permisos) {
        Set<Permiso> deRol = new HashSet<>();
        for (PermisoCodigo codigo : permisos) {
            deRol.add(Permiso.builder().codigo(codigo).build());
        }
        Usuario encargado = Usuario.builder()
                .usuario(login)
                .estadoUsuario(EstadoUsuario.ACTIVO)
                .rol(Rol.builder().nombre("ENCARGADO").permisos(deRol).build())
                .build();
        encargado.setId(99L);
        return encargado;
    }

    private MovimientoCaja movimientoGuardado() {
        ArgumentCaptor<MovimientoCaja> mov = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(mov.capture());
        return mov.getValue();
    }

    @Test
    @DisplayName("obtenerCajaAbierta informa el estado del efectivo y si los retiros requieren confirmación")
    void obtenerCajaAbiertaConControlEfectivo() {

        Caja caja = conCajaAbierta("1000");
        retiroRequiereConfirmacion(true);
        EstadoEfectivoCaja estado = new EstadoEfectivoCaja(
                new BigDecimal("800"), new BigDecimal("1000.00"), true, 2, 1);
        when(controlEfectivoCaja.estado(any(), any())).thenReturn(Optional.of(estado));

        CajaDto dto = cajaService.obtenerCajaAbierta().orElseThrow();

        assertSame(estado, dto.getEstadoEfectivo());
        assertTrue(dto.isRetiroRequiereConfirmacion());
        verify(controlEfectivoCaja).estado(org.mockito.ArgumentMatchers.same(caja), any());
    }

    @Test
    @DisplayName("sin confirmación configurada, el retiro no guarda quién lo confirmó")
    void retiroSinConfirmacion() {

        conCajaAbierta("1000");
        retiroRequiereConfirmacion(false);
        loguearSin(PermisoCodigo.CAJA_CONFIRMAR_RETIRO);

        cajaService.registrarMovimiento(TipoMovimientoCaja.RETIRO, new BigDecimal("200"), "Retiro");

        assertNull(movimientoGuardado().getUsuarioAutorizacion());
        verifyNoInteractions(authenticationService);
    }

    @Test
    @DisplayName("con confirmación, un usuario con CAJA_CONFIRMAR_RETIRO confirma su propio retiro sin credenciales")
    void retiroConfirmadoPorElMismoUsuario() {

        conCajaAbierta("1000");
        retiroRequiereConfirmacion(true);

        cajaService.registrarMovimiento(TipoMovimientoCaja.RETIRO, new BigDecimal("200"), "Retiro");

        assertSame(sesionUsuario.getUsuario(), movimientoGuardado().getUsuarioAutorizacion());
        verifyNoInteractions(authenticationService);
    }

    @Test
    @DisplayName("con confirmación, el retiro de un cajero sin permiso se rechaza si no hay credenciales")
    void retiroSinCredenciales() {

        conCajaAbierta("1000");
        retiroRequiereConfirmacion(true);
        loguearSin(PermisoCodigo.CAJA_CONFIRMAR_RETIRO);

        BusinessException ex = assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(
                TipoMovimientoCaja.RETIRO, new BigDecimal("200"), "Retiro", "  ", null));

        assertTrue(ex.getMessage().contains("encargado"));
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("con confirmación, el encargado confirma con su usuario y contraseña sin cambiar la sesión")
    void retiroConfirmadoPorEncargado() {

        conCajaAbierta("1000");
        retiroRequiereConfirmacion(true);
        loguearSin(PermisoCodigo.CAJA_CONFIRMAR_RETIRO);
        Usuario cajero = sesionUsuario.getUsuario();
        Usuario encargado = encargado("encargado", PermisoCodigo.CAJA_CONFIRMAR_RETIRO);
        when(authenticationService.authenticate("encargado", "clave")).thenReturn(encargado);

        cajaService.registrarMovimiento(
                TipoMovimientoCaja.RETIRO, new BigDecimal("200"), "Retiro", " encargado ", "clave");

        MovimientoCaja movimiento = movimientoGuardado();
        assertSame(cajero, movimiento.getUsuario());
        assertSame(encargado, movimiento.getUsuarioAutorizacion());
        assertSame(cajero, sesionUsuario.getUsuario());
    }

    @Test
    @DisplayName("con confirmación, se rechazan las credenciales inválidas")
    void retiroConCredencialesInvalidas() {

        conCajaAbierta("1000");
        retiroRequiereConfirmacion(true);
        loguearSin(PermisoCodigo.CAJA_CONFIRMAR_RETIRO);
        when(authenticationService.authenticate("encargado", "mal"))
                .thenThrow(new AuthenticationException("Usuario o contraseña incorrectos."));

        assertThrows(AuthenticationException.class, () -> cajaService.registrarMovimiento(
                TipoMovimientoCaja.RETIRO, new BigDecimal("200"), "Retiro", "encargado", "mal"));
        verify(movimientoCajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("con confirmación, el que confirma tiene que tener CAJA_CONFIRMAR_RETIRO")
    void retiroConfirmadoPorUsuarioSinPermiso() {

        conCajaAbierta("1000");
        retiroRequiereConfirmacion(true);
        loguearSin(PermisoCodigo.CAJA_CONFIRMAR_RETIRO);
        when(authenticationService.authenticate("otro", "clave"))
                .thenReturn(encargado("otro", PermisoCodigo.CAJA_VER));

        AuthorizationException ex = assertThrows(AuthorizationException.class, () -> cajaService
                .registrarMovimiento(TipoMovimientoCaja.RETIRO, new BigDecimal("200"), "Retiro", "otro", "clave"));

        assertTrue(ex.getMessage().contains("otro"));
        verify(movimientoCajaRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = TipoMovimientoCaja.class, names = {"INGRESO_MANUAL", "EGRESO_MANUAL", "GASTO"})
    @DisplayName("con confirmación de retiros, los demás movimientos manuales no la necesitan")
    void otrosMovimientosNoRequierenConfirmacion(TipoMovimientoCaja tipo) {

        conCajaAbierta("1000");
        retiroRequiereConfirmacion(true);
        loguearSin(PermisoCodigo.CAJA_CONFIRMAR_RETIRO);

        cajaService.registrarMovimiento(tipo, new BigDecimal("200"), "Motivo");

        assertNull(movimientoGuardado().getUsuarioAutorizacion());
        verifyNoInteractions(authenticationService);
    }

    @Test
    @DisplayName("un retiro que supera el saldo se rechaza antes de pedir credenciales")
    void retiroQueSuperaElSaldoNoPideCredenciales() {

        conCajaAbierta("100");
        retiroRequiereConfirmacion(true);
        loguearSin(PermisoCodigo.CAJA_CONFIRMAR_RETIRO);

        assertThrows(BusinessException.class, () -> cajaService.registrarMovimiento(
                TipoMovimientoCaja.RETIRO, new BigDecimal("500"), "Retiro", "encargado", "clave"));
        verifyNoInteractions(authenticationService);
    }

    // ------------------------------------------------------------------
    // Cierre
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cerrar registra fecha, saldo esperado, saldo final y diferencia (faltante)")
    void cerrarConFaltante() {

        Caja caja = conCajaAbierta("1000",
                movimiento(TipoMovimientoCaja.VENTA, "500"),
                movimiento(TipoMovimientoCaja.GASTO, "200"));

        CajaDto dto = cajaService.cerrar(new BigDecimal("1250"), "  Faltan 50  ");

        assertEquals(EstadoCaja.CERRADA, caja.getEstadoCaja());
        assertNotNull(caja.getFechaCierre());
        assertEquals(new BigDecimal("1300.00"), caja.getSaldoEsperado());
        assertEquals(new BigDecimal("1250.00"), caja.getSaldoFinal());
        assertEquals(new BigDecimal("-50.00"), caja.getDiferencia());
        assertEquals("Faltan 50", caja.getObservacionCierre());
        assertSame(sesionUsuario.getUsuario(), caja.getUsuarioCierre());
        verify(cajaRepository).save(caja);

        assertEquals(EstadoCaja.CERRADA, dto.getEstadoCaja());
        assertEquals(new BigDecimal("1300.00"), dto.getSaldoEsperado());
        assertEquals(new BigDecimal("500.00"), dto.getTotalIngresos());
        assertEquals(new BigDecimal("200.00"), dto.getTotalEgresos());
    }

    @Test
    @DisplayName("cerrar con sobrante deja diferencia positiva y observación opcional")
    void cerrarConSobrante() {

        Caja caja = conCajaAbierta("100");

        cajaService.cerrar(new BigDecimal("120"), "   ");

        assertEquals(new BigDecimal("20.00"), caja.getDiferencia());
        assertEquals(null, caja.getObservacionCierre());
    }

    @Test
    @DisplayName("cerrar sin caja abierta falla")
    void cerrarSinCajaAbierta() {

        sinCajaAbierta();

        assertThrows(BusinessException.class, () -> cajaService.cerrar(BigDecimal.TEN, null));
        verify(cajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cerrar valida el saldo contado")
    void cerrarValidaSaldoContado() {

        assertThrows(BusinessException.class, () -> cajaService.cerrar(null, null));
        assertThrows(BusinessException.class, () -> cajaService.cerrar(new BigDecimal("-1"), null));
        verify(cajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cerrar requiere CAJA_CERRAR")
    void cerrarSinPermiso() {

        loguearSin(PermisoCodigo.CAJA_CERRAR);

        assertThrows(AuthorizationException.class, () -> cajaService.cerrar(BigDecimal.TEN, null));
        verifyNoInteractions(cajaRepository, movimientoCajaRepository);
    }

    // ------------------------------------------------------------------
    // Caja de otro usuario
    // ------------------------------------------------------------------

    private static final Long OTRO_USUARIO_ID = 8L;
    private static final Long CAJA_OTRO_ID = 2L;

    private Usuario otroUsuario() {
        Usuario usuario = Usuario.builder().usuario("cajero").nombre("Ana").apellido("Pérez").build();
        usuario.setId(OTRO_USUARIO_ID);
        return usuario;
    }

    /** Caja abierta de otro usuario, con los movimientos indicados además de la apertura. */
    private Caja conCajaAbiertaDeOtro(Usuario responsable, String saldoInicial, MovimientoCaja... movimientos) {
        Caja caja = Caja.builder()
                .saldoInicial(new BigDecimal(saldoInicial))
                .estadoCaja(EstadoCaja.ABIERTA)
                .usuario(responsable)
                .usuarioApertura(responsable)
                .build();
        caja.setId(CAJA_OTRO_ID);
        List<MovimientoCaja> lista = new ArrayList<>();
        lista.add(movimiento(TipoMovimientoCaja.APERTURA, saldoInicial));
        lista.addAll(List.of(movimientos));
        lenient().when(cajaRepository.findById(CAJA_OTRO_ID)).thenReturn(Optional.of(caja));
        lenient().when(movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(CAJA_OTRO_ID)).thenReturn(lista);
        return caja;
    }

    @Test
    @DisplayName("abrir para otro usuario lo deja como responsable y guarda quién la abrió")
    void abrirParaOtroUsuario() {

        Usuario otro = otroUsuario();
        when(usuarioRepository.findById(OTRO_USUARIO_ID)).thenReturn(Optional.of(otro));

        cajaService.abrir(OTRO_USUARIO_ID, new BigDecimal("500"));

        ArgumentCaptor<Caja> caja = ArgumentCaptor.forClass(Caja.class);
        verify(cajaRepository).save(caja.capture());
        assertSame(otro, caja.getValue().getUsuario());
        assertSame(sesionUsuario.getUsuario(), caja.getValue().getUsuarioApertura());
        assertEquals(new BigDecimal("500.00"), caja.getValue().getSaldoInicial());

        ArgumentCaptor<MovimientoCaja> mov = ArgumentCaptor.forClass(MovimientoCaja.class);
        verify(movimientoCajaRepository).save(mov.capture());
        assertSame(sesionUsuario.getUsuario(), mov.getValue().getUsuario());
        verify(cajaRepository).existsByUsuarioIdAndEstadoCaja(OTRO_USUARIO_ID, EstadoCaja.ABIERTA);
    }

    @Test
    @DisplayName("abrir para otro usuario requiere CAJA_ABRIR_OTRO_USUARIO")
    void abrirParaOtroUsuarioSinPermiso() {

        loguearSin(PermisoCodigo.CAJA_ABRIR_OTRO_USUARIO);

        assertThrows(AuthorizationException.class,
                () -> cajaService.abrir(OTRO_USUARIO_ID, BigDecimal.TEN));
        verifyNoInteractions(usuarioRepository, cajaRepository, movimientoCajaRepository);
    }

    @Test
    @DisplayName("sin CAJA_ABRIR_OTRO_USUARIO se puede abrir la caja propia indicando el propio usuario")
    void abrirPropiaSinPermisoDeOtroUsuario() {

        loguearSin(PermisoCodigo.CAJA_ABRIR_OTRO_USUARIO);

        cajaService.abrir(USUARIO_ID, BigDecimal.TEN);

        ArgumentCaptor<Caja> caja = ArgumentCaptor.forClass(Caja.class);
        verify(cajaRepository).save(caja.capture());
        assertSame(sesionUsuario.getUsuario(), caja.getValue().getUsuario());
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("no se puede abrir una caja a un usuario que ya tiene una abierta")
    void abrirParaOtroUsuarioConCajaAbierta() {

        when(usuarioRepository.findById(OTRO_USUARIO_ID)).thenReturn(Optional.of(otroUsuario()));
        when(cajaRepository.existsByUsuarioIdAndEstadoCaja(OTRO_USUARIO_ID, EstadoCaja.ABIERTA)).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cajaService.abrir(OTRO_USUARIO_ID, BigDecimal.TEN));

        assertTrue(ex.getMessage().contains("Ana Pérez (cajero) ya tiene una caja abierta"));
        verify(cajaRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoUsuario.class, names = {"INACTIVO", "BLOQUEADO"})
    @DisplayName("no se puede abrir una caja a un usuario inactivo o bloqueado")
    void abrirParaUsuarioNoActivo(EstadoUsuario estado) {

        Usuario otro = otroUsuario();
        otro.setEstadoUsuario(estado);
        when(usuarioRepository.findById(OTRO_USUARIO_ID)).thenReturn(Optional.of(otro));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cajaService.abrir(OTRO_USUARIO_ID, BigDecimal.TEN));

        assertTrue(ex.getMessage().contains("no está activo"));
        verify(cajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("no se puede abrir una caja a un usuario dado de baja")
    void abrirParaUsuarioDadoDeBaja() {

        Usuario otro = otroUsuario();
        otro.setEstadoRegistro(EstadoRegistro.INACTIVO);
        when(usuarioRepository.findById(OTRO_USUARIO_ID)).thenReturn(Optional.of(otro));

        assertThrows(BusinessException.class, () -> cajaService.abrir(OTRO_USUARIO_ID, BigDecimal.TEN));
        verify(cajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("abrir para un usuario inexistente falla")
    void abrirParaUsuarioInexistente() {

        when(usuarioRepository.findById(OTRO_USUARIO_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> cajaService.abrir(OTRO_USUARIO_ID, BigDecimal.TEN));
        verify(cajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("listarResponsablesDisponibles devuelve los usuarios activos con su nombre completo")
    void listarResponsablesDisponibles() {

        when(usuarioRepository.findByEstadoRegistroAndEstadoUsuarioOrderByApellidoAscNombreAsc(
                EstadoRegistro.ACTIVO, EstadoUsuario.ACTIVO)).thenReturn(List.of(otroUsuario()));

        List<ResponsableCajaDto> responsables = cajaService.listarResponsablesDisponibles();

        assertEquals(List.of(new ResponsableCajaDto(OTRO_USUARIO_ID, "cajero", "Ana Pérez")), responsables);
    }

    @Test
    @DisplayName("listarResponsablesDisponibles requiere CAJA_ABRIR_OTRO_USUARIO")
    void listarResponsablesDisponiblesSinPermiso() {

        loguearSin(PermisoCodigo.CAJA_ABRIR_OTRO_USUARIO);

        assertThrows(AuthorizationException.class, () -> cajaService.listarResponsablesDisponibles());
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("cerrar la caja de otro usuario guarda el arqueo y quién la cerró")
    void cerrarCajaDeOtroUsuario() {

        Usuario otro = otroUsuario();
        Caja caja = conCajaAbiertaDeOtro(otro, "1000", movimiento(TipoMovimientoCaja.VENTA, "300"));

        CajaDto dto = cajaService.cerrar(CAJA_OTRO_ID, new BigDecimal("1300"), null);

        assertEquals(EstadoCaja.CERRADA, caja.getEstadoCaja());
        assertEquals(new BigDecimal("1300.00"), caja.getSaldoEsperado());
        assertEquals(new BigDecimal("0.00"), caja.getDiferencia());
        assertSame(otro, caja.getUsuario());
        assertSame(sesionUsuario.getUsuario(), caja.getUsuarioCierre());
        verify(cajaRepository).save(caja);
        assertEquals(EstadoCaja.CERRADA, dto.getEstadoCaja());
    }

    @Test
    @DisplayName("cerrar la caja de otro usuario requiere CAJA_CERRAR_OTRO_USUARIO")
    void cerrarCajaDeOtroUsuarioSinPermiso() {

        loguearSin(PermisoCodigo.CAJA_CERRAR_OTRO_USUARIO);
        Caja caja = conCajaAbiertaDeOtro(otroUsuario(), "100");

        assertThrows(AuthorizationException.class, () -> cajaService.cerrar(CAJA_OTRO_ID, BigDecimal.TEN, null));
        assertEquals(EstadoCaja.ABIERTA, caja.getEstadoCaja());
        verify(cajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("sin CAJA_CERRAR_OTRO_USUARIO se puede cerrar la caja propia por su id")
    void cerrarCajaPropiaPorIdSinPermisoDeOtroUsuario() {

        loguearSin(PermisoCodigo.CAJA_CERRAR_OTRO_USUARIO);
        Caja caja = conCajaAbierta("100");
        when(cajaRepository.findById(CAJA_ID)).thenReturn(Optional.of(caja));

        cajaService.cerrar(CAJA_ID, new BigDecimal("100"), null);

        assertEquals(EstadoCaja.CERRADA, caja.getEstadoCaja());
        assertSame(sesionUsuario.getUsuario(), caja.getUsuarioCierre());
    }

    @Test
    @DisplayName("cerrar una caja ya cerrada falla")
    void cerrarCajaYaCerrada() {

        Caja caja = conCajaAbiertaDeOtro(otroUsuario(), "100");
        caja.setEstadoCaja(EstadoCaja.CERRADA);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cajaService.cerrar(CAJA_OTRO_ID, BigDecimal.TEN, null));

        assertTrue(ex.getMessage().contains("ya está cerrada"));
        verify(cajaRepository, never()).save(any());
    }

    @Test
    @DisplayName("cerrar una caja inexistente falla")
    void cerrarCajaInexistente() {

        when(cajaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> cajaService.cerrar(99L, BigDecimal.TEN, null));
        assertThrows(BusinessException.class, () -> cajaService.cerrar((Long) null, BigDecimal.TEN, null));
    }

    @Test
    @DisplayName("listarCajasAbiertasDeOtrosUsuarios excluye la caja propia y calcula los totales")
    void listarCajasAbiertasDeOtrosUsuarios() {

        Caja propia = cajaAbierta("100");
        Caja deOtro = conCajaAbiertaDeOtro(otroUsuario(), "200");
        MovimientoCaja venta = movimiento(TipoMovimientoCaja.VENTA, "50");
        venta.setCaja(deOtro);

        when(cajaRepository.findAbiertas()).thenReturn(List.of(propia, deOtro));
        when(movimientoCajaRepository.findByCajaIdIn(List.of(CAJA_OTRO_ID))).thenReturn(List.of(venta));

        List<CajaDto> cajas = cajaService.listarCajasAbiertasDeOtrosUsuarios();

        assertEquals(1, cajas.size());
        assertEquals(CAJA_OTRO_ID, cajas.get(0).getId());
        assertEquals(new BigDecimal("50.00"), cajas.get(0).getTotalIngresos());
        assertEquals(new BigDecimal("250.00"), cajas.get(0).getSaldoEsperado());
    }

    @Test
    @DisplayName("listarCajasAbiertasDeOtrosUsuarios requiere CAJA_CERRAR_OTRO_USUARIO")
    void listarCajasAbiertasDeOtrosUsuariosSinPermiso() {

        loguearSin(PermisoCodigo.CAJA_CERRAR_OTRO_USUARIO);

        assertThrows(AuthorizationException.class, () -> cajaService.listarCajasAbiertasDeOtrosUsuarios());
        verifyNoInteractions(cajaRepository);
    }

    // ------------------------------------------------------------------
    // Historial
    // ------------------------------------------------------------------

    /** Caja propia abierta, caja abierta de otro usuario (más reciente) y una cerrada. */
    private List<Caja> cajasParaHistorial() {

        Caja propia = cajaAbierta("100");
        propia.setFechaApertura(LocalDateTime.of(2026, 11, 20, 8, 0));
        Caja deOtro = conCajaAbiertaDeOtro(otroUsuario(), "200");
        deOtro.setFechaApertura(LocalDateTime.of(2026, 11, 21, 9, 0));
        Caja cerrada = cajaAbierta("50");
        cerrada.setId(3L);
        cerrada.setEstadoCaja(EstadoCaja.CERRADA);
        cerrada.setSaldoEsperado(new BigDecimal("50.00"));

        lenient().when(cajaRepository.findAbiertas()).thenReturn(List.of(propia, deOtro));
        lenient().when(cajaRepository.findCerradas(any(), any())).thenReturn(List.of(cerrada));

        return List.of(propia, deOtro, cerrada);
    }

    @Test
    @DisplayName("listarHistorial muestra primero las abiertas (más recientes primero), incluidas las de otros, y después las cerradas")
    void listarHistorial() {

        cajasParaHistorial();

        List<CajaDto> cajas = cajaService.listarHistorial(null, null, FiltroCaja.TODAS);

        assertEquals(List.of(CAJA_OTRO_ID, CAJA_ID, 3L), cajas.stream().map(CajaDto::getId).toList());
    }

    @Test
    @DisplayName("listarHistorial sin CAJA_VER_ABIERTAS_TODAS oculta las abiertas de otros usuarios")
    void listarHistorialSinVerAbiertasAjenas() {

        cajasParaHistorial();
        loguearSin(PermisoCodigo.CAJA_VER_ABIERTAS_TODAS);

        List<CajaDto> cajas = cajaService.listarHistorial(null, null, null);

        assertEquals(List.of(CAJA_ID, 3L), cajas.stream().map(CajaDto::getId).toList());
    }

    @Test
    @DisplayName("listarHistorial sin CAJA_VER_CERRADAS_TODAS muestra sólo las cerradas propias")
    void listarHistorialSinVerCerradasAjenas() {

        cajasParaHistorial();
        Caja cerradaDeOtro = conCajaAbiertaDeOtro(otroUsuario(), "70");
        cerradaDeOtro.setId(4L);
        cerradaDeOtro.setEstadoCaja(EstadoCaja.CERRADA);
        Caja cerradaPropia = cajaAbierta("50");
        cerradaPropia.setId(3L);
        cerradaPropia.setEstadoCaja(EstadoCaja.CERRADA);
        when(cajaRepository.findCerradas(any(), any())).thenReturn(List.of(cerradaDeOtro, cerradaPropia));

        assertEquals(List.of(4L, 3L), cajaService.listarHistorial(null, null, FiltroCaja.CERRADAS)
                .stream().map(CajaDto::getId).toList());

        loguearSin(PermisoCodigo.CAJA_VER_CERRADAS_TODAS);

        assertEquals(List.of(CAJA_OTRO_ID, CAJA_ID, 3L), cajaService.listarHistorial(null, null, null)
                .stream().map(CajaDto::getId).toList());
        assertEquals(List.of(3L), cajaService.listarCajasCerradas(null, null)
                .stream().map(CajaDto::getId).toList());
    }

    @Test
    @DisplayName("listarMovimientos de una caja ajena requiere el permiso de ver todas las cajas en su estado")
    void listarMovimientosDeCajaAjena() {

        Caja deOtro = conCajaAbiertaDeOtro(otroUsuario(), "200");

        // Abierta: alcanza con CAJA_VER_ABIERTAS_TODAS o con CAJA_CERRAR_OTRO_USUARIO.
        loguearSin(PermisoCodigo.CAJA_VER_ABIERTAS_TODAS);
        cajaService.listarMovimientos(CAJA_OTRO_ID);

        loguearCon(EnumSet.of(PermisoCodigo.CAJA_VER, PermisoCodigo.CAJA_VER_CERRADAS_TODAS));
        assertThrows(AuthorizationException.class, () -> cajaService.listarMovimientos(CAJA_OTRO_ID));

        // Cerrada: sólo con CAJA_VER_CERRADAS_TODAS.
        deOtro.setEstadoCaja(EstadoCaja.CERRADA);
        cajaService.listarMovimientos(CAJA_OTRO_ID);

        loguearSin(PermisoCodigo.CAJA_VER_CERRADAS_TODAS);
        assertThrows(AuthorizationException.class, () -> cajaService.listarMovimientos(CAJA_OTRO_ID));
    }

    @Test
    @DisplayName("sin los permisos de ver todas las cajas se ven los movimientos de las propias")
    void listarMovimientosDeCajaPropiaSinPermisosAmplios() {

        loguearCon(EnumSet.of(PermisoCodigo.CAJA_VER));
        Caja propia = cajaAbierta("10");
        propia.setEstadoCaja(EstadoCaja.CERRADA);
        when(cajaRepository.findById(CAJA_ID)).thenReturn(Optional.of(propia));

        cajaService.listarMovimientos(CAJA_ID);

        verify(movimientoCajaRepository).findByCajaIdOrderByFechaAscIdAsc(CAJA_ID);
    }

    @Test
    @DisplayName("listarHistorial filtra por estado")
    void listarHistorialPorEstado() {

        cajasParaHistorial();

        assertEquals(List.of(CAJA_OTRO_ID, CAJA_ID), cajaService.listarHistorial(null, null, FiltroCaja.ABIERTAS)
                .stream().map(CajaDto::getId).toList());
        assertEquals(List.of(3L), cajaService.listarHistorial(null, null, FiltroCaja.CERRADAS)
                .stream().map(CajaDto::getId).toList());
    }

    @Test
    @DisplayName("listarHistorial muestra las abiertas que se abrieron hasta la fecha 'hasta' y filtra las cerradas por cierre")
    void listarHistorialPorFechas() {

        cajasParaHistorial();
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 20);

        List<CajaDto> cajas = cajaService.listarHistorial(desde, hasta, FiltroCaja.TODAS);

        // La caja del otro usuario se abrió el 21: queda afuera.
        assertEquals(List.of(CAJA_ID, 3L), cajas.stream().map(CajaDto::getId).toList());
        verify(cajaRepository).findCerradas(desde.atStartOfDay(), DateUtils.finDelDia(hasta));
    }

    @Test
    @DisplayName("listarHistorial requiere CAJA_VER y rechaza un rango invertido")
    void listarHistorialValidaciones() {

        assertThrows(BusinessException.class, () -> cajaService.listarHistorial(
                LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 1), null));

        loguearSin(PermisoCodigo.CAJA_VER);

        assertThrows(AuthorizationException.class, () -> cajaService.listarHistorial(null, null, null));
        verifyNoInteractions(cajaRepository);
    }

}
