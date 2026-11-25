package org.gestionComercio.service.impl;

import org.gestionComercio.dto.caja.EstadoEfectivoCaja;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlEfectivoCajaTest {

    private static final LocalDateTime APERTURA = LocalDateTime.of(2026, 11, 23, 8, 0);
    private static final BigDecimal MONTO = new BigDecimal("2000");

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private MovimientoCajaRepository movimientoCajaRepository;

    @Mock
    private VentaRepository ventaRepository;

    private ControlEfectivoCaja control;

    /** Movimientos de la prueba; cada uno, un minuto después del anterior. */
    private final List<MovimientoCaja> movimientos = new ArrayList<>();

    @BeforeEach
    void setUp() {
        control = new ControlEfectivoCaja(empresaRepository, movimientoCajaRepository, ventaRepository);
    }

    private LocalDateTime mov(TipoMovimientoCaja tipo, String importe) {
        LocalDateTime fecha = APERTURA.plusMinutes(movimientos.size() + 1L);
        movimientos.add(MovimientoCaja.builder().tipo(tipo).importe(new BigDecimal(importe)).fecha(fecha).build());
        return fecha;
    }

    private ControlEfectivoCaja.Recorrido recorrer(String saldoInicial) {
        return ControlEfectivoCaja.recorrer(new BigDecimal(saldoInicial), APERTURA, movimientos, MONTO);
    }

    private Caja caja(String saldoInicial) {
        Caja caja = Caja.builder().saldoInicial(new BigDecimal(saldoInicial)).fechaApertura(APERTURA).build();
        caja.setId(1L);
        return caja;
    }

    private void empresa(String monto, Integer clientes) {
        when(empresaRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(Empresa.builder()
                .montoMaximoCaja(monto == null ? null : new BigDecimal(monto))
                .clientesAntesDeBloqueo(clientes)
                .build()));
    }

    // ------------------------------------------------------------------
    // Recorrido de los movimientos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("por debajo del monto no hay alcance")
    void debajoDelMonto() {
        mov(TipoMovimientoCaja.APERTURA, "1000");
        mov(TipoMovimientoCaja.VENTA, "999.99");

        ControlEfectivoCaja.Recorrido recorrido = recorrer("1000");

        assertEquals(new BigDecimal("1999.99"), recorrido.saldo());
        assertNull(recorrido.alcance());
    }

    @Test
    @DisplayName("el alcance es el movimiento con el que el saldo llega al monto (igual o más)")
    void alcanceAlLlegarAlMonto() {
        mov(TipoMovimientoCaja.APERTURA, "1000");
        mov(TipoMovimientoCaja.VENTA, "500");
        LocalDateTime llega = mov(TipoMovimientoCaja.VENTA, "500");
        mov(TipoMovimientoCaja.VENTA, "700");

        ControlEfectivoCaja.Recorrido recorrido = recorrer("1000");

        assertEquals(new BigDecimal("2700.00"), recorrido.saldo());
        assertEquals(llega, recorrido.alcance());
    }

    @Test
    @DisplayName("si el saldo inicial ya alcanza el monto, el alcance es la apertura")
    void alcanceEnLaApertura() {
        mov(TipoMovimientoCaja.APERTURA, "2500");

        assertEquals(APERTURA, recorrer("2500").alcance());
    }

    @Test
    @DisplayName("al bajar del monto se pierde el alcance y, si vuelve a llegar, empieza de nuevo")
    void alcanceSeReinicia() {
        mov(TipoMovimientoCaja.APERTURA, "1000");
        mov(TipoMovimientoCaja.VENTA, "1500");
        mov(TipoMovimientoCaja.RETIRO, "600");
        assertNull(recorrer("1000").alcance());

        LocalDateTime vuelve = mov(TipoMovimientoCaja.INGRESO_MANUAL, "200");

        assertEquals(vuelve, recorrer("1000").alcance());
    }

    @Test
    @DisplayName("un gasto o la anulación de una venta también pueden bajar el saldo del monto")
    void otrosEgresosBajanElSaldo() {
        mov(TipoMovimientoCaja.APERTURA, "1000");
        mov(TipoMovimientoCaja.VENTA, "1500");
        mov(TipoMovimientoCaja.ANULACION_VENTA, "1500");

        assertNull(recorrer("1000").alcance());
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sin monto máximo configurado no hay control ni consultas de ventas")
    void sinMontoMaximo() {
        empresa(null, 3);

        assertTrue(control.estado(caja("50000"), movimientos).isEmpty());
        assertDoesNotThrow(() -> control.verificarQuePuedeVender(caja("50000")));
        verifyNoInteractions(ventaRepository);
    }

    @Test
    @DisplayName("sin alcanzar el monto no se cuentan ventas")
    void sinAlcanzar() {
        empresa("2000", 1);
        mov(TipoMovimientoCaja.VENTA, "500");

        EstadoEfectivoCaja estado = control.estado(caja("1000"), movimientos).orElseThrow();

        assertFalse(estado.montoAlcanzado());
        assertFalse(estado.bloqueada());
        assertNull(estado.clientesRestantes());
        assertNull(estado.mensaje());
        verifyNoInteractions(ventaRepository);
    }

    @Test
    @DisplayName("alcanzado el monto, cuenta las ventas registradas después del alcance")
    void cuentaVentasDesdeElAlcance() {
        empresa("2000", 3);
        LocalDateTime alcance = mov(TipoMovimientoCaja.VENTA, "1500");
        when(ventaRepository.countByCajaIdAndFechaAfterAndEstadoVentaNotIn(
                eq(1L), eq(alcance), eq(ControlEfectivoCaja.NO_CUENTAN))).thenReturn(2L);

        EstadoEfectivoCaja estado = control.estado(caja("1000"), movimientos).orElseThrow();

        assertTrue(estado.montoAlcanzado());
        assertEquals(new BigDecimal("2500.00"), estado.saldo());
        assertEquals(2, estado.ventasDesdeAlcance());
        assertEquals(1, estado.clientesRestantes());
        assertFalse(estado.bloqueada());
    }

    @Test
    @DisplayName("verificarQuePuedeVender rechaza la venta con la caja bloqueada")
    void ventaRechazadaConCajaBloqueada() {
        empresa("2000", 0);
        mov(TipoMovimientoCaja.VENTA, "1500");
        when(movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(1L)).thenReturn(movimientos);
        when(ventaRepository.countByCajaIdAndFechaAfterAndEstadoVentaNotIn(any(), any(), any())).thenReturn(0L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> control.verificarQuePuedeVender(caja("1000")));

        assertTrue(ex.getMessage().contains("bloqueada"));
    }

    // ------------------------------------------------------------------
    // EstadoEfectivoCaja
    // ------------------------------------------------------------------

    @Test
    @DisplayName("bloqueada cuando las ventas desde el alcance llegan a los clientes permitidos")
    void bloqueo() {
        assertFalse(new EstadoEfectivoCaja(MONTO, new BigDecimal("2500"), true, 2, 1).bloqueada());
        assertTrue(new EstadoEfectivoCaja(MONTO, new BigDecimal("2500"), true, 2, 2).bloqueada());
        assertTrue(new EstadoEfectivoCaja(MONTO, new BigDecimal("2500"), true, 0, 0).bloqueada());
        assertFalse(new EstadoEfectivoCaja(MONTO, new BigDecimal("2500"), true, null, 50).bloqueada());
        assertFalse(new EstadoEfectivoCaja(MONTO, new BigDecimal("1500"), false, 0, 0).bloqueada());
    }

    @Test
    @DisplayName("el mensaje indica cuánto retirar y cuántos clientes quedan")
    void mensaje() {
        EstadoEfectivoCaja dosMas = new EstadoEfectivoCaja(MONTO, new BigDecimal("2500"), true, 3, 1);
        EstadoEfectivoCaja unoMas = new EstadoEfectivoCaja(MONTO, new BigDecimal("2500"), true, 3, 2);
        EstadoEfectivoCaja bloqueada = new EstadoEfectivoCaja(MONTO, new BigDecimal("2500"), true, 3, 3);

        assertEquals(new BigDecimal("500.01"), dosMas.minimoARetirar());
        assertTrue(dosMas.mensaje().contains("2 clientes más"));
        assertTrue(unoMas.mensaje().contains("1 cliente más"));
        assertTrue(bloqueada.mensaje().contains("bloqueada"));
    }
}
