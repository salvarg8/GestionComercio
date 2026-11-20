package org.gestionComercio.dto.compra;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.EstadoCompra;
import org.gestionComercio.enums.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Una compra a un proveedor con sus renglones.
 *
 * <p>Para registrarla se informan el proveedor, la forma de pago, el número
 * de factura (opcional), la fecha (opcional, ahora por defecto), el estado
 * (opcional: RECIBIDA por defecto, o PENDIENTE si la mercadería todavía no
 * llegó), si se actualiza el precio de compra de los productos y los
 * renglones. El usuario, la caja y los totales los completa el servicio.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CompraDto extends BaseDto {

    /** Fecha de la compra (de la factura); al registrar, null = ahora. No puede ser futura. */
    private LocalDateTime fecha;

    /** Número de la factura del proveedor (opcional). */
    private String numeroFactura;

    private Long proveedorId;

    /** Razón social del proveedor. */
    private String proveedorNombre;

    private FormaPago formaPago;

    /** Suma de los subtotales de los renglones. */
    private BigDecimal subtotal;

    /** Importe total de la compra (igual al subtotal). */
    private BigDecimal total;

    /** Al registrar: null = RECIBIDA. Sólo se aceptan PENDIENTE y RECIBIDA. */
    private EstadoCompra estadoCompra;

    /**
     * Sólo al registrar: true para guardar el precio de cada renglón como el
     * nuevo precio de compra del producto. No se lee de la base.
     */
    private boolean actualizarPreciosCompra;

    /** Caja de donde salió el pago (sólo en compras pagadas en efectivo). */
    private Long cajaId;

    private Long usuarioId;

    /** Nombre de usuario (login) de quien registró la compra. */
    private String usuarioNombre;

    /** Nombre y apellido de quien registró la compra. */
    private String usuarioNombreCompleto;

    /** Sólo en compras anuladas. */
    private LocalDateTime fechaAnulacion;

    /** Sólo en compras anuladas. */
    private String motivoAnulacion;

    /** Nombre y apellido de quien anuló la compra (sólo en compras anuladas). */
    private String usuarioAnulacionNombreCompleto;

    @Builder.Default
    private List<DetalleCompraDto> detalles = new ArrayList<>();

}
