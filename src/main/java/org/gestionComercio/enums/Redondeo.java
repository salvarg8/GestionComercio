package org.gestionComercio.enums;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Múltiplo al que se redondean los importes: el total de las ventas (siempre
 * hacia abajo, la diferencia se descuenta) y los precios de la actualización
 * masiva (al más cercano).
 */
public enum Redondeo implements Descriptible {

    SIN_REDONDEO(null, "Sin redondeo"),
    DIEZ_CENTAVOS(new BigDecimal("0.10"), "A los 10 centavos"),
    UN_PESO(BigDecimal.ONE, "Al peso"),
    DIEZ_PESOS(BigDecimal.TEN, "A los 10 pesos"),
    CIEN_PESOS(new BigDecimal("100"), "A los 100 pesos");

    /** Múltiplo; null si no se redondea. */
    private final BigDecimal multiplo;
    private final String descripcion;

    Redondeo(BigDecimal multiplo, String descripcion) {
        this.multiplo = multiplo;
        this.descripcion = descripcion;
    }

    /**
     * Redondea hacia abajo al múltiplo (el cliente nunca paga de más), con dos
     * decimales. Ej.: al peso, 1.234,56 → 1.234,00.
     *
     * @return null si el importe es null
     */
    public BigDecimal haciaAbajo(BigDecimal importe) {
        return redondear(importe, RoundingMode.FLOOR);
    }

    /**
     * Redondeo del total de una venta: hacia abajo, salvo que el total sea
     * menor que el múltiplo (quedaría en cero); en ese caso no se redondea.
     * Ej.: a los 100 pesos, 1.250 → 1.200, pero 50 → 50.
     *
     * @return null si el importe es null
     */
    public BigDecimal totalDeVenta(BigDecimal importe) {

        BigDecimal redondeado = haciaAbajo(importe);

        if (redondeado != null && redondeado.signum() == 0 && importe.signum() > 0) {
            return importe.setScale(2, RoundingMode.HALF_UP);
        }

        return redondeado;
    }

    /**
     * Redondea al múltiplo más cercano (la mitad, hacia arriba), con dos
     * decimales. Ej.: a los 10 pesos, 1.235,00 → 1.240,00.
     *
     * @return null si el importe es null
     */
    public BigDecimal alMasCercano(BigDecimal importe) {
        return redondear(importe, RoundingMode.HALF_UP);
    }

    /**
     * Redondea hacia arriba al múltiplo, con dos decimales. Ej.: a los 10
     * pesos, 3,00 → 10,00.
     *
     * @return null si el importe es null
     */
    public BigDecimal haciaArriba(BigDecimal importe) {
        return redondear(importe, RoundingMode.CEILING);
    }

    private BigDecimal redondear(BigDecimal importe, RoundingMode modo) {

        if (importe == null) {
            return null;
        }

        if (multiplo == null) {
            return importe.setScale(2, RoundingMode.HALF_UP);
        }

        return importe.divide(multiplo, 0, modo).multiply(multiplo).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
