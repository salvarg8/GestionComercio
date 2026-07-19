package org.gestionComercio.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.enums.AlicuotaIVA;
import org.gestionComercio.enums.UnidadMedida;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "productos",
        indexes = {
                @Index(name = "idx_producto_codigo", columnList = "codigo"),
                @Index(name = "idx_producto_codigo_barras", columnList = "codigo_barras"),
                @Index(name = "idx_producto_nombre", columnList = "nombre")
        }
)
public class Producto extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "codigo_barras", unique = true, length = 20)
    private String codigoBarras;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioCompra = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioVenta = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer stockMinimo = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean manejaStock = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean permiteStockNegativo = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private UnidadMedida unidadMedida = UnidadMedida.UNIDAD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AlicuotaIVA alicuotaIVA = AlicuotaIVA.IVA_21;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id", nullable = false)
    private Marca marca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Builder.Default
    @OneToMany(mappedBy = "producto", fetch = FetchType.LAZY)
    private List<DetalleVenta> detallesVenta = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "producto", fetch = FetchType.LAZY)
    private List<DetalleCompra> detallesCompra = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "producto", fetch = FetchType.LAZY)
    private List<MovimientoStock> movimientosStock = new ArrayList<>();

}