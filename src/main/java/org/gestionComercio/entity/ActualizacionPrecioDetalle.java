package org.gestionComercio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Precios de un producto antes y después de una actualización masiva. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "actualizaciones_precios_detalle")
public class ActualizacionPrecioDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actualizacion_id", nullable = false)
    private ActualizacionPrecio actualizacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioCompraAnterior;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioCompraNuevo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVentaAnterior;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVentaNuevo;

}
