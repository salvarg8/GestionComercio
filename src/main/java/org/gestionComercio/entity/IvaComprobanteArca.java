package org.gestionComercio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** IVA de un comprobante electrónico en una alícuota (AlicIva de WSFEv1). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class IvaComprobanteArca {

    /** Id de la alícuota en ARCA (5 = 21 %, 4 = 10,5 %, ...). */
    @Column(name = "alicuota_arca", nullable = false)
    private Integer alicuotaArca;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

}
