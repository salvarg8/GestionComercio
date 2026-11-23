package org.gestionComercio.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.TipoComprobante;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "ventas")
public class Venta extends BaseEntity {

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoComprobante tipoComprobante;

    /**
     * Correlativo dentro del tipo de comprobante (lo asigna
     * {@code NumeradorComprobanteRepository}). Null en las facturas: su número
     * lo da ARCA y está en {@link #getFacturaArca()}.
     */
    private Long numeroComprobante;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal descuento;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FormaPago formaPago;

    /** Null si la venta es a consumidor final (sin cliente identificado). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caja_id")
    private Caja caja;

    @Builder.Default
    @OneToMany(
            mappedBy = "venta",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<DetalleVenta> detalles = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoVenta estadoVenta = EstadoVenta.PAGADA;

    // Anulación: los tres datos se completan juntos, sólo en ventas ANULADAS.

    private LocalDateTime fechaAnulacion;

    @Column(length = 255)
    private String motivoAnulacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_anulacion_id")
    private Usuario usuarioAnulacion;

    /** Factura electrónica y, si se anuló después de autorizada, su nota de crédito. */
    @Builder.Default
    @OneToMany(mappedBy = "venta")
    @OrderBy("id")
    @BatchSize(size = 50)
    private List<ComprobanteArca> comprobantesArca = new ArrayList<>();

    /** La factura electrónica de la venta; null si no es una factura. */
    public ComprobanteArca getFacturaArca() {
        return comprobantesArca.stream()
                .filter(c -> c.getTipoComprobante().esFactura())
                .findFirst()
                .orElse(null);
    }

    /** La nota de crédito que anula la factura; null si no tiene. */
    public ComprobanteArca getNotaCreditoArca() {
        return comprobantesArca.stream()
                .filter(c -> c.getTipoComprobante().esNotaCredito())
                .findFirst()
                .orElse(null);
    }

}