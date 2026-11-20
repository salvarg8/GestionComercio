package org.gestionComercio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.enums.AlicuotaIVA;
import org.hibernate.annotations.ColumnDefault;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.util.CantidadUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    /*
     * El código interno y el código de barras son únicos sólo entre los
     * productos activos (índices únicos parciales en V1__esquema_inicial.sql),
     * por eso no llevan unique = true.
     */
    @Column(nullable = false, length = 50)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "codigo_barras", length = 20)
    private String codigoBarras;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioCompra = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal precioVenta = BigDecimal.ZERO;

    /**
     * Sin setter a propósito: el stock sólo cambia al aplicar un movimiento de
     * inventario ({@link #aplicarMovimiento}), que registra
     * {@code InventarioService}. Así toda variación queda en el historial.
     * Admite decimales si la unidad de medida es fraccionable.
     */
    @Column(nullable = false, precision = CantidadUtils.PRECISION, scale = CantidadUtils.ESCALA)
    @Builder.Default
    @Setter(AccessLevel.NONE)
    private BigDecimal stock = BigDecimal.ZERO;

    /**
     * Control de concurrencia optimista: cada actualización incrementa la
     * versión y Hibernate rechaza guardar un producto que otra transacción
     * modificó después de que se leyó. Evita que dos operaciones simultáneas
     * calculen el stock sobre el mismo valor y una pise a la otra.
     *
     * <p>El default 0 deja a los productos cargados por fuera de Hibernate
     * (scripts, migración de datos) con una versión válida.
     */
    @Version
    @ColumnDefault("0")
    @Column(nullable = false)
    @Setter(AccessLevel.NONE)
    private Long version;

    @Column(nullable = false, precision = CantidadUtils.PRECISION, scale = CantidadUtils.ESCALA)
    @Builder.Default
    private BigDecimal stockMinimo = BigDecimal.ZERO;

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
    private List<MovimientoInventario> movimientosInventario = new ArrayList<>();

    /**
     * Deja el stock en cero. Sólo se usa al dar de alta el producto: el stock de
     * un producto ya guardado únicamente cambia con movimientos de inventario.
     *
     * @throws IllegalStateException si el producto ya está guardado
     */
    public void inicializarStock() {

        if (getId() != null) {
            throw new IllegalStateException(
                    "El stock de un producto existente sólo se modifica con movimientos de inventario.");
        }

        this.stock = BigDecimal.ZERO;
    }

    /**
     * Actualiza el stock con el resultado del movimiento. Verifica que el
     * movimiento sea de este producto y que se haya calculado sobre su stock
     * actual, para que el historial nunca quede desalineado.
     *
     * @throws IllegalStateException si el movimiento no corresponde al stock actual
     */
    public void aplicarMovimiento(MovimientoInventario movimiento) {

        Objects.requireNonNull(movimiento, "El movimiento es obligatorio.");

        if (movimiento.getProducto() != this) {
            throw new IllegalStateException("El movimiento corresponde a otro producto.");
        }

        BigDecimal stockActual = stock == null ? BigDecimal.ZERO : stock;

        if (movimiento.getStockAnterior().compareTo(stockActual) != 0) {
            throw new IllegalStateException(
                    "El stock del producto cambió desde que se calculó el movimiento.");
        }

        this.stock = movimiento.getStockResultante();
    }

}