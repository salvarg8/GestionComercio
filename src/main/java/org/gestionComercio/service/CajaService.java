package org.gestionComercio.service;

import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.dto.caja.MovimientoCajaDto;
import org.gestionComercio.dto.caja.ResponsableCajaDto;
import org.gestionComercio.enums.FiltroCaja;
import org.gestionComercio.enums.TipoMovimientoCaja;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Apertura, movimientos y cierre de caja. Cada usuario trabaja sobre su propia
 * caja y no puede tener más de una abierta a la vez. Con los permisos
 * correspondientes, un usuario (por ejemplo, un encargado) puede abrir y
 * cerrar la caja de otro; la caja registra quién la abrió y quién la cerró.
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
     * Abre una caja para el responsable indicado y registra el movimiento de
     * APERTURA con el saldo inicial. La caja guarda como usuario de apertura
     * al de la sesión. Requiere CAJA_ABRIR y, si el responsable es otro
     * usuario, también CAJA_ABRIR_OTRO_USUARIO.
     *
     * @param responsableId usuario que trabajará con la caja; null para el usuario de la sesión
     * @param saldoInicial  efectivo con el que se abre (cero o más)
     * @throws org.gestionComercio.exception.BusinessException si el responsable
     *         no está activo o ya tiene una caja abierta
     * @throws org.gestionComercio.exception.EntityNotFoundException si el responsable no existe
     */
    CajaDto abrir(Long responsableId, BigDecimal saldoInicial);

    /**
     * Usuarios activos a los que se les puede abrir una caja, incluido el de
     * la sesión, por apellido y nombre. Requiere CAJA_ABRIR_OTRO_USUARIO.
     */
    List<ResponsableCajaDto> listarResponsablesDisponibles();

    /**
     * Caja abierta del usuario de la sesión, con sus totales, el saldo
     * esperado al momento, la situación del efectivo frente al monto máximo
     * de la empresa y si los retiros requieren confirmación. Requiere CAJA_VER.
     */
    Optional<CajaDto> obtenerCajaAbierta();

    /**
     * Registra un movimiento manual sin confirmación de un encargado (ver
     * {@link #registrarMovimiento(TipoMovimientoCaja, BigDecimal, String, String, String)}).
     */
    MovimientoCajaDto registrarMovimiento(TipoMovimientoCaja tipo, BigDecimal importe, String descripcion);

    /**
     * Registra un movimiento manual en la caja abierta del usuario de la
     * sesión. Requiere CAJA_REGISTRAR_MOVIMIENTO.
     *
     * <p>Si la empresa exige confirmar los retiros, un RETIRO lo confirma un
     * usuario con CAJA_CONFIRMAR_RETIRO: el de la sesión si tiene el permiso
     * (las credenciales se ignoran) o, si no, el encargado cuyas credenciales
     * se indican. La sesión no cambia. El movimiento guarda quién lo confirmó.
     *
     * @param tipo               INGRESO_MANUAL, EGRESO_MANUAL, GASTO o RETIRO
     * @param importe            mayor que cero, con hasta dos decimales
     * @param descripcion        motivo del movimiento (obligatorio)
     * @param usuarioEncargado   usuario que confirma el retiro; null si no hace falta
     * @param passwordEncargado  su contraseña
     * @throws org.gestionComercio.exception.BusinessException si no hay caja
     *         abierta, el tipo no es manual, el egreso supera el saldo o falta
     *         la confirmación del retiro
     * @throws org.gestionComercio.exception.AuthenticationException si las credenciales no son válidas
     * @throws org.gestionComercio.exception.AuthorizationException si el encargado no puede confirmar retiros
     */
    MovimientoCajaDto registrarMovimiento(TipoMovimientoCaja tipo, BigDecimal importe, String descripcion,
                                          String usuarioEncargado, String passwordEncargado);

    /**
     * Cajas cerradas, de la más reciente a la más antigua según la fecha de
     * cierre, con sus totales. Requiere CAJA_VER; sin CAJA_VER_CERRADAS_TODAS
     * sólo devuelve las del usuario de la sesión.
     *
     * @param desde fecha de cierre mínima (incluida); null para no limitar
     * @param hasta fecha de cierre máxima (incluida); null para no limitar
     * @throws org.gestionComercio.exception.BusinessException si desde es posterior a hasta
     */
    List<CajaDto> listarCajasCerradas(LocalDate desde, LocalDate hasta);

    /**
     * Historial de cajas, abiertas y cerradas, con sus totales. Requiere CAJA_VER.
     *
     * <p>Primero las abiertas (de la más reciente a la más antigua según la
     * apertura) y después las cerradas (igual que {@link #listarCajasCerradas}).
     * Siempre se ven las cajas propias; las abiertas de otros usuarios, con
     * CAJA_VER_ABIERTAS_TODAS, y las cerradas de otros, con
     * CAJA_VER_CERRADAS_TODAS.
     *
     * <p>Las fechas filtran las cerradas por fecha de cierre; las abiertas
     * aparecen si se abrieron hasta la fecha {@code hasta} (siguen abiertas,
     * así que estuvieron activas en el período).
     *
     * @param desde  fecha mínima (incluida); null para no limitar
     * @param hasta  fecha máxima (incluida); null para no limitar
     * @param filtro estado a mostrar; null = todas
     * @throws org.gestionComercio.exception.BusinessException si desde es posterior a hasta
     */
    List<CajaDto> listarHistorial(LocalDate desde, LocalDate hasta, FiltroCaja filtro);

    /**
     * Movimientos de una caja, en el orden en que se registraron. Requiere
     * CAJA_VER y, si la caja es de otro usuario, CAJA_VER_ABIERTAS_TODAS o
     * CAJA_VER_CERRADAS_TODAS según su estado (una abierta también se ve con
     * CAJA_CERRAR_OTRO_USUARIO, para poder cerrarla).
     *
     * @throws org.gestionComercio.exception.AuthorizationException si no puede ver esa caja
     */
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

    /**
     * Cierra con arqueo la caja indicada, igual que {@link #cerrar(BigDecimal, String)}.
     * La caja guarda como usuario de cierre al de la sesión. Requiere
     * CAJA_CERRAR y, si la caja es de otro usuario, también CAJA_CERRAR_OTRO_USUARIO.
     *
     * @param cajaId       caja a cerrar
     * @param saldoContado efectivo contado (cero o más)
     * @param observacion  opcional, por ejemplo para explicar una diferencia
     * @throws org.gestionComercio.exception.BusinessException si la caja ya está cerrada
     * @throws org.gestionComercio.exception.EntityNotFoundException si la caja no existe
     */
    CajaDto cerrar(Long cajaId, BigDecimal saldoContado, String observacion);

    /**
     * Cajas abiertas de los demás usuarios (sin la del usuario de la sesión),
     * por responsable, con sus totales y el saldo esperado al momento.
     * Requiere CAJA_CERRAR_OTRO_USUARIO.
     */
    List<CajaDto> listarCajasAbiertasDeOtrosUsuarios();

}
