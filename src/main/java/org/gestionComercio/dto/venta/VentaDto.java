package org.gestionComercio.dto.venta;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.TipoComprobante;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Una venta con sus renglones.
 *
 * <p>Para registrarla se informan el tipo de comprobante (opcional, ticket por
 * defecto), el cliente (opcional), la forma de pago, el descuento (opcional,
 * importe en pesos) y los renglones. La fecha, el número de comprobante, el
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

    /**
     * Al registrar: null = ticket. Se aceptan ticket, remito y, con la
     * facturación electrónica habilitada, la factura que corresponda al cliente.
     */
    private TipoComprobante tipoComprobante;

    /** Correlativo dentro del tipo; lo asigna el servicio. Null en las facturas (ver {@link #factura}). */
    private Long numeroComprobante;

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

    /** Sólo en ventas anuladas. */
    private LocalDateTime fechaAnulacion;

    /** Sólo en ventas anuladas. */
    private String motivoAnulacion;

    /** Nombre y apellido de quien anuló la venta (sólo en ventas anuladas). */
    private String usuarioAnulacionNombreCompleto;

    @Builder.Default
    private List<DetalleVentaDto> detalles = new ArrayList<>();

    /** Factura electrónica (sólo en facturas). */
    private ComprobanteArcaDto factura;

    /** Nota de crédito electrónica (sólo en facturas anuladas después de autorizadas). */
    private ComprobanteArcaDto notaCredito;

    /**
     * "Ticket N° 00000012", "Factura B N° 0003-00000123" o "Factura B
     * (pendiente de CAE)"; vacío si la venta todavía no tiene comprobante.
     */
    public String getComprobante() {

        if (factura != null) {
            return factura.getComprobante();
        }

        return TipoComprobante.formatear(tipoComprobante, numeroComprobante);
    }

}
