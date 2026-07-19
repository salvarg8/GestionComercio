package org.gestionComercio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.enums.AlicuotaIVA;
import org.gestionComercio.enums.UnidadMedida;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductoDto extends BaseDto {

    private String codigo;

    private String codigoBarras;

    private String nombre;

    private String descripcion;

    private BigDecimal precioCompra;

    private BigDecimal precioVenta;

    private Integer stock;

    private Integer stockMinimo;

    private Boolean manejaStock;

    private Boolean permiteStockNegativo;

    private UnidadMedida unidadMedida;

    private AlicuotaIVA alicuotaIVA;

    private Long categoriaId;

    private Long marcaId;

    private Long proveedorId;

}