package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.caja.EstadoEfectivoCaja;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.VentaRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Control del efectivo en caja (configuración de la empresa):
 *
 * <ul>
 *     <li>cuando el saldo esperado llega al monto máximo, hay que avisar al cajero;</li>
 *     <li>alcanzado el monto se permiten tantas ventas más como indique la
 *         empresa; superadas, la caja queda bloqueada para vender;</li>
 *     <li>la caja vuelve a vender cuando el saldo baja del monto máximo. Si
 *         después lo vuelve a alcanzar, la cuenta de ventas empieza de nuevo.</li>
 * </ul>
 *
 * <p>No guarda estado: lo calcula recorriendo los movimientos de la caja en
 * orden y contando las ventas registradas desde la última vez que el saldo
 * alcanzó el monto. Se cuentan las ventas no anuladas ni canceladas, sea cual
 * sea su forma de pago.
 */
@Component
@RequiredArgsConstructor
public class ControlEfectivoCaja {

    /** Estados de venta que no cuentan como cliente atendido. */
    static final Set<EstadoVenta> NO_CUENTAN = EnumSet.of(EstadoVenta.ANULADA, EstadoVenta.CANCELADA);

    private final EmpresaRepository empresaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final VentaRepository ventaRepository;

    /**
     * @return la situación del efectivo de la caja, o vacío si la empresa no
     *         configuró un monto máximo
     */
    public Optional<EstadoEfectivoCaja> estado(Caja caja) {
        return estado(caja, movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(caja.getId()));
    }

    /**
     * Igual que {@link #estado(Caja)} con los movimientos ya consultados.
     *
     * @param movimientos movimientos de la caja en el orden en que se registraron
     */
    public Optional<EstadoEfectivoCaja> estado(Caja caja, List<MovimientoCaja> movimientos) {

        Empresa empresa = empresaRepository.findFirstByOrderByIdAsc().orElse(null);

        if (empresa == null || empresa.getMontoMaximoCaja() == null) {
            return Optional.empty();
        }

        BigDecimal monto = empresa.getMontoMaximoCaja();
        Recorrido recorrido = recorrer(caja.getSaldoInicial(), caja.getFechaApertura(), movimientos, monto);

        int ventas = 0;

        if (recorrido.alcance() != null) {
            ventas = (int) ventaRepository.countByCajaIdAndFechaAfterAndEstadoVentaNotIn(
                    caja.getId(), recorrido.alcance(), NO_CUENTAN);
        }

        return Optional.of(new EstadoEfectivoCaja(monto, recorrido.saldo(), recorrido.alcance() != null,
                empresa.getClientesAntesDeBloqueo(), ventas));
    }

    /**
     * Rechaza la venta si la caja está bloqueada por exceso de efectivo.
     *
     * @throws BusinessException si hay que retirar dinero antes de vender
     */
    public void verificarQuePuedeVender(Caja caja) {

        estado(caja).filter(EstadoEfectivoCaja::bloqueada).ifPresent(estado -> {
            throw new BusinessException(estado.mensaje());
        });
    }

    /**
     * Recorre los movimientos en orden calculando el saldo, y registra el
     * momento en que el saldo alcanzó el monto por última vez sin volver a
     * bajar.
     *
     * @param apertura    momento de la apertura: si el saldo inicial ya alcanza el monto, el alcance es ése
     * @param movimientos movimientos de la caja en el orden en que se registraron
     * @return el saldo final y el momento del alcance vigente (null si el saldo está por debajo del monto)
     */
    static Recorrido recorrer(BigDecimal saldoInicial, LocalDateTime apertura,
                              List<MovimientoCaja> movimientos, BigDecimal monto) {

        BigDecimal saldo = saldoInicial;
        LocalDateTime alcance = saldo.compareTo(monto) >= 0 ? apertura : null;

        for (MovimientoCaja movimiento : movimientos) {

            int signo = movimiento.getTipo().getSigno();

            if (signo == 0) {
                continue;
            }

            saldo = saldo.add(movimiento.getImporte().multiply(BigDecimal.valueOf(signo)));

            if (saldo.compareTo(monto) < 0) {
                alcance = null;
            } else if (alcance == null) {
                alcance = movimiento.getFecha();
            }
        }

        return new Recorrido(saldo.setScale(2), alcance);
    }

    record Recorrido(BigDecimal saldo, LocalDateTime alcance) {
    }
}
