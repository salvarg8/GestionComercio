package org.gestionComercio.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProductoDTO extends BaseDTO {

    private String codigo;

    private String nombre;

    private BigDecimal precioCompra;

    private BigDecimal precioVenta;

    private Long categoriaId;

    private Long marcaId;

    private Long proveedorId;

    private Boolean permiteStockNegativo;

}