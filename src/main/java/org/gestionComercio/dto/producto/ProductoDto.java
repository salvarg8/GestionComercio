package org.gestionComercio.dto.producto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.AlicuotaIVA;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.UnidadMedida;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductoDto extends BaseDto {

    /** Código interno. Obligatorio y único (sin distinguir mayúsculas). */
    private String codigo;

    /** Opcional y único. Vacío se guarda como null. */
    private String codigoBarras;

    private String nombre;

    private String descripcion;

    private BigDecimal precioCompra;

    private BigDecimal precioVenta;

    /**
     * Sólo lectura: el stock cambia con movimientos de stock (compras, ventas,
     * ajustes), nunca al guardar el producto.
     */
    private BigDecimal stock;

    private BigDecimal stockMinimo;

    private Boolean manejaStock;

    private Boolean permiteStockNegativo;

    private UnidadMedida unidadMedida;

    private AlicuotaIVA alicuotaIVA;

    private Long categoriaId;

    private Long marcaId;

    private Long proveedorId;

    /** Sólo lectura, para mostrar en listados. */
    private String categoriaNombre;

    /** Sólo lectura, para mostrar en listados. */
    private String marcaNombre;

    /** Sólo lectura, para mostrar en listados. */
    private String proveedorNombre;

    /** Sólo lectura: el estado se cambia con darDeBaja/habilitar, nunca al guardar. */
    private EstadoRegistro estadoRegistro;

    /**
     * Versión del producto cuando se leyó. Hay que devolverla al actualizar: si
     * otro usuario lo modificó mientras tanto, la actualización se rechaza en
     * lugar de pisar sus cambios.
     */
    private Long version;

    public boolean isActivo() {
        return estadoRegistro == null || estadoRegistro == EstadoRegistro.ACTIVO;
    }

    /** true si maneja stock y el stock actual está en el mínimo o por debajo. */
    public boolean isStockBajo() {
        return Boolean.TRUE.equals(manejaStock)
                && stock != null
                && stockMinimo != null
                && stock.compareTo(stockMinimo) <= 0;
    }
}
