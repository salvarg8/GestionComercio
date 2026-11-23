package org.gestionComercio.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.enums.AmbienteArca;
import org.gestionComercio.enums.EstadoComprobanteArca;
import org.gestionComercio.enums.TipoComprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprobante electrónico de una venta: su factura o, si se anuló después de
 * autorizada, su nota de crédito.
 *
 * <p>El número lo asigna ARCA (último autorizado + 1) al momento de enviarlo;
 * hasta entonces queda null. Los importes, el receptor y las alícuotas son los
 * que se enviaron, para imprimir el comprobante y armar el QR tal como se
 * autorizó.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "comprobantes_arca")
public class ComprobanteArca extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoComprobante tipoComprobante;

    /** La factura que anula esta nota de crédito (CbtesAsoc); null en las facturas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprobante_asociado_id")
    private ComprobanteArca comprobanteAsociado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoComprobanteArca estado = EstadoComprobanteArca.PENDIENTE;

    // Emisor: los de la configuración vigente al enviarlo.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AmbienteArca ambiente;

    @Column(nullable = false, length = 15)
    private String cuitEmisor;

    @Column(nullable = false)
    private Integer puntoVenta;

    /** Null mientras no se envió (o si ARCA lo rechazó: no consumió el número). */
    private Long numero;

    /** Fecha del comprobante (CbteFch). */
    private LocalDate fecha;

    @Column(length = 14)
    private String cae;

    private LocalDate vencimientoCae;

    // Receptor

    private Integer docTipo;

    private Long docNro;

    private Integer condicionIvaReceptor;

    // Importes

    @Column(precision = 12, scale = 2)
    private BigDecimal importeTotal;

    /** Neto gravado (ImpNeto). En las facturas C, igual al total. */
    @Column(precision = 12, scale = 2)
    private BigDecimal importeNeto;

    @Column(precision = 12, scale = 2)
    private BigDecimal importeIva;

    /** Operaciones exentas (ImpOpEx). */
    @Column(precision = 12, scale = 2)
    private BigDecimal importeExento;

    @ElementCollection
    @CollectionTable(name = "comprobantes_arca_iva", joinColumns = @JoinColumn(name = "comprobante_arca_id"))
    @OrderColumn(name = "orden")
    @Builder.Default
    private List<IvaComprobanteArca> alicuotas = new ArrayList<>();

    /** Observaciones o motivos de rechazo de ARCA, o el último error de comunicación. */
    @Column(columnDefinition = "text")
    private String observaciones;

    /** Último envío o consulta a ARCA. */
    private LocalDateTime fechaProceso;

}
