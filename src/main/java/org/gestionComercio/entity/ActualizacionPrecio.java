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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.enums.PrecioActualizado;
import org.gestionComercio.enums.Redondeo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro de auditoría de una actualización masiva de precios: quién y
 * cuándo (auditoría de {@link BaseEntity}), con qué criterio y el precio
 * anterior y el nuevo de cada producto. No se modifica ni se borra.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "actualizaciones_precios")
public class ActualizacionPrecio extends BaseEntity {

    /** Aumento (positivo) o rebaja (negativa), en porcentaje. */
    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal porcentaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrecioActualizado precio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Redondeo redondeo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_id")
    private Proveedor proveedor;

    /** Los filtros en texto, como se vieron al aplicarla (ej.: "Categoría: Bebidas"). */
    @Column(nullable = false, length = 255)
    private String filtro;

    @Column(nullable = false)
    private Integer cantidadProductos;

    @Builder.Default
    @OneToMany(mappedBy = "actualizacion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ActualizacionPrecioDetalle> detalles = new ArrayList<>();

}
