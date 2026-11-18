package org.gestionComercio.dto.venta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Una venta con sus renglones.
 *
 * <p>Para registrarla se informan el cliente (opcional), la forma de pago, el
 * descuento (opcional, importe en pesos) y los renglones. La fecha, el
 * usuario, la caja, los precios y los totales los completa el servicio.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class VentaDto extends BaseDto {

    public static final String CONSUMIDOR_FINAL = "Consumidor final";

    private LocalDateTime fecha;

    /** Null para consumidor final. */
    private Long clienteId;

    /** "Apellido, Nombre" del cliente, o "Consumidor final" si no tiene. */
    private String clienteNombre;

    private FormaPago formaPago;

    /** Suma de los subtotales de los renglones. */
    private BigDecimal subtotal;

    /** Importe que se descuenta del subtotal (cero o más). */
    private BigDecimal descuento;

    /** Subtotal − descuento. */
    private BigDecimal total;

    private EstadoVenta estadoVenta;

    private Long cajaId;

    private Long usuarioId;

    /** Nombre de usuario (login) del vendedor. */
    private String usuarioNombre;

    /** Nombre y apellido del vendedor. */
    private String usuarioNombreCompleto;

    @Builder.Default
    private List<DetalleVentaDto> detalles = new ArrayList<>();

}
