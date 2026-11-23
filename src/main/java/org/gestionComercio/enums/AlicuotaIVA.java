package org.gestionComercio.enums;

import java.math.BigDecimal;

public enum AlicuotaIVA implements Descriptible {

    EXENTO("Exento", BigDecimal.ZERO, null),

    IVA_105("10.5%", new BigDecimal("10.50"), 4),

    IVA_21("21%", new BigDecimal("21.00"), 5),

    IVA_27("27%", new BigDecimal("27.00"), 6);

    private final String descripcion;

    private final BigDecimal porcentaje;

    /** Id de la alícuota en ARCA ({@code FEParamGetTiposIva}); null si es exento (va en ImpOpEx). */
    private final Integer codigoArca;

    AlicuotaIVA(String descripcion, BigDecimal porcentaje, Integer codigoArca) {
        this.descripcion = descripcion;
        this.porcentaje = porcentaje;
        this.codigoArca = codigoArca;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getPorcentaje() {
        return porcentaje;
    }

    public Integer getCodigoArca() {
        return codigoArca;
    }

    /** La alícuota del id de ARCA, o null si no es una de las que maneja el sistema. */
    public static AlicuotaIVA deCodigoArca(int codigo) {

        for (AlicuotaIVA alicuota : values()) {
            if (alicuota.codigoArca != null && alicuota.codigoArca == codigo) {
                return alicuota;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
