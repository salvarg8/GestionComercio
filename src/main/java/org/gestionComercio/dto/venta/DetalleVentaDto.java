package org.gestionComercio.dto.venta;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.UnidadMedida;

import java.math.BigDecimal;

/**
 * Renglón de una venta. Para registrarla sólo se informan el producto y la
 * cantidad: el precio unitario lo toma el servicio del producto y el subtotal
 * lo calcula él; el resto de los campos se completan al leerla.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DetalleVentaDto extends BaseDto {

    private Long productoId;

    private String productoCodigo;

    private String productoNombre;

    private UnidadMedida productoUnidadMedida;

    private BigDecimal cantidad;

    /** Precio de venta del producto al momento de la venta. */
    private BigDecimal precioUnitario;

    /** Cantidad × precio unitario, redondeado a dos decimales. */
    private BigDecimal subtotal;

}
