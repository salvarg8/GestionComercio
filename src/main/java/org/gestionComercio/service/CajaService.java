package org.gestionComercio.service;

import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.dto.caja.MovimientoCajaDto;
import org.gestionComercio.enums.TipoMovimientoCaja;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Apertura, movimientos y cierre de caja. Cada usuario trabaja sobre su propia
 * caja y no puede tener más de una abierta a la vez.
 *
 * <p>Los importes se guardan positivos con dos decimales; el tipo de
 * movimiento indica si suma o resta. El saldo esperado es
 * saldo inicial + ingresos − egresos. Ningún egreso puede dejar el saldo
 * esperado por debajo de cero.
 */
public interface CajaService {

    /**
     * Abre una caja para el usuario de la sesión y registra el movimiento de
     * APERTURA con el saldo inicial. Requiere CAJA_ABRIR.
     *
     * @param saldoInicial efectivo con el que se abre (cero o más)
     * @throws org.gestionComercio.exception.BusinessException si el usuario ya tiene una caja abierta
     */
    CajaDto abrir(BigDecimal saldoInicial);

    /**
     * Caja abierta del usuario de la sesión, con sus totales y el saldo
     * esperado al momento. Requiere CAJA_VER.
     */
    Optional<CajaDto> obtenerCajaAbierta();

    /**
     * Registra un movimiento manual en la caja abierta del usuario de la
     * sesión. Requiere CAJA_REGISTRAR_MOVIMIENTO.
     *
     * @param tipo        INGRESO_MANUAL, EGRESO_MANUAL, GASTO o RETIRO
     * @param importe     mayor que cero, con hasta dos decimales
     * @param descripcion motivo del movimiento (obligatorio)
     * @throws org.gestionComercio.exception.BusinessException si no hay caja
     *         abierta, el tipo no es manual o el egreso supera el saldo
     */
    MovimientoCajaDto registrarMovimiento(TipoMovimientoCaja tipo, BigDecimal importe, String descripcion);

    /**
     * Cajas cerradas de todos los usuarios, de la más reciente a la más
     * antigua según la fecha de cierre, con sus totales. Requiere CAJA_VER.
     *
     * @param desde fecha de cierre mínima (incluida); null para no limitar
     * @param hasta fecha de cierre máxima (incluida); null para no limitar
     * @throws org.gestionComercio.exception.BusinessException si desde es posterior a hasta
     */
    List<CajaDto> listarCajasCerradas(LocalDate desde, LocalDate hasta);

    /** Movimientos de una caja, en el orden en que se registraron. Requiere CAJA_VER. */
    List<MovimientoCajaDto> listarMovimientos(Long cajaId);

    /**
     * Cierra la caja abierta del usuario de la sesión con el arqueo: guarda
     * la fecha de cierre, el saldo esperado, el saldo contado (saldo final) y
     * la diferencia. Requiere CAJA_CERRAR.
     *
     * @param saldoContado efectivo contado (cero o más)
     * @param observacion  opcional, por ejemplo para explicar una diferencia
     */
    CajaDto cerrar(BigDecimal saldoContado, String observacion);

}
