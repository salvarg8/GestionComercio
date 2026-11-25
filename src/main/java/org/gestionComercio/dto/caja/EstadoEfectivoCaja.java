package org.gestionComercio.dto.caja;

import org.gestionComercio.util.CurrencyUtils;

import java.math.BigDecimal;

/**
 * Situación del efectivo de una caja abierta frente al monto máximo que
 * configura la empresa. Se calcula siempre a partir de los movimientos y las
 * ventas de la caja; no se guarda.
 *
 * @param montoMaximo            saldo a partir del cual hay que retirar dinero
 * @param saldo                  saldo esperado de la caja al momento
 * @param montoAlcanzado         si el saldo llegó al monto máximo (saldo ≥ monto)
 * @param clientesAntesDeBloqueo ventas permitidas una vez alcanzado el monto; null = nunca se bloquea
 * @param ventasDesdeAlcance     ventas no anuladas registradas desde que se alcanzó el monto
 */
public record EstadoEfectivoCaja(
        BigDecimal montoMaximo,
        BigDecimal saldo,
        boolean montoAlcanzado,
        Integer clientesAntesDeBloqueo,
        int ventasDesdeAlcance) {

    /** Si la caja no puede registrar ventas hasta que se retire dinero. */
    public boolean bloqueada() {
        return montoAlcanzado && clientesAntesDeBloqueo != null && ventasDesdeAlcance >= clientesAntesDeBloqueo;
    }

    /**
     * Ventas que todavía se pueden registrar antes del bloqueo.
     *
     * @return null si no se alcanzó el monto o la caja nunca se bloquea
     */
    public Integer clientesRestantes() {

        if (!montoAlcanzado || clientesAntesDeBloqueo == null) {
            return null;
        }

        return Math.max(0, clientesAntesDeBloqueo - ventasDesdeAlcance);
    }

    /** Lo mínimo que hay que retirar para que el saldo baje del monto máximo. */
    public BigDecimal minimoARetirar() {
        return montoAlcanzado ? saldo.subtract(montoMaximo).add(new BigDecimal("0.01")) : BigDecimal.ZERO;
    }

    /**
     * Mensaje para el cajero.
     *
     * @return null si el monto no se alcanzó
     */
    public String mensaje() {

        if (!montoAlcanzado) {
            return null;
        }

        String situacion = "El efectivo en caja (" + CurrencyUtils.format(saldo)
                + ") alcanzó el monto máximo (" + CurrencyUtils.format(montoMaximo) + ").";

        String retiro = " Retire al menos " + CurrencyUtils.format(minimoARetirar()) + ".";

        if (bloqueada()) {
            return situacion + " La caja está bloqueada: no se pueden registrar ventas hasta retirar dinero."
                    + retiro;
        }

        Integer restantes = clientesRestantes();

        if (restantes == null) {
            return situacion + " Debe retirar dinero." + retiro;
        }

        return situacion + " Puede atender " + restantes + (restantes == 1 ? " cliente más" : " clientes más")
                + " antes de que se bloquee la caja." + retiro;
    }
}
