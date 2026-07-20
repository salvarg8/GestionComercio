package org.gestionComercio.enums;

import java.math.BigDecimal;

public enum AlicuotaIVA implements Descriptible {

    EXENTO("Exento", BigDecimal.ZERO),

    IVA_105("10.5%", new BigDecimal("10.50")),

    IVA_21("21%", new BigDecimal("21.00")),

    IVA_27("27%", new BigDecimal("27.00"));

    private final String descripcion;

    private final BigDecimal porcentaje;

    AlicuotaIVA(String descripcion, BigDecimal porcentaje) {
        this.descripcion = descripcion;
        this.porcentaje = porcentaje;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}