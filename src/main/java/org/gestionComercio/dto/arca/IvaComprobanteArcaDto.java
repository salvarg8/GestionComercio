package org.gestionComercio.dto.arca;

import org.gestionComercio.enums.AlicuotaIVA;

import java.math.BigDecimal;

/**
 * IVA de un comprobante en una alícuota.
 *
 * @param alicuota la alícuota del sistema; null si ARCA usa una que el sistema no maneja
 */
public record IvaComprobanteArcaDto(int alicuotaArca, AlicuotaIVA alicuota, BigDecimal baseImponible,
                                    BigDecimal importe) {

    /** "IVA 21%", o el id de ARCA si no es una alícuota del sistema. */
    public String getDescripcion() {
        return "IVA " + (alicuota == null ? "(alícuota " + alicuotaArca + ")" : alicuota.getDescripcion());
    }

}
