package org.gestionComercio.dto.compra;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.UnidadMedida;

import java.math.BigDecimal;

/**
 * Renglón de una compra. Para registrarla se informan el producto, la
 * cantidad y el precio de compra unitario de la factura; el subtotal lo
 * calcula el servicio y el resto de los campos se completan al leerla.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DetalleCompraDto extends BaseDto {

    private Long productoId;

    private String productoCodigo;

    private String productoNombre;

    private UnidadMedida productoUnidadMedida;

    private BigDecimal cantidad;

    /** Precio unitario pagado al proveedor. */
    private BigDecimal precioCompra;

    /** Cantidad × precio de compra, redondeado a dos decimales. */
    private BigDecimal subtotal;

}
