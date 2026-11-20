package org.gestionComercio.validation;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.entity.DetalleVenta;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.repository.ClienteRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.util.CurrencyUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Valida una venta nueva ya normalizada por el servicio (descuento null como
 * cero, lista de renglones no nula). Informa todos los errores juntos.
 *
 * <p>No controla el stock: lo hace el servicio de inventario al descontarlo,
 * que también informa juntos todos los faltantes.
 *
 * <p>Los errores de un renglón usan el campo {@code detalles[i].campo}
 * (i desde cero) y su mensaje indica el número de renglón (desde uno).
 */
@Component
@RequiredArgsConstructor
public class VentaValidator implements Validator<VentaDto> {

    /** Mayor importe que admite una columna precision 12, scale 2. */
    public static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("9999999999.99");

    public static final int IMPORTE_DECIMALES_MAX = 2;

    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;

    @Override
    public ValidationResult validate(VentaDto dto) {

        ValidationResult result = new ValidationResult();

        validarTipoComprobante(dto.getTipoComprobante(), result);
        validarFormaPagoYCliente(dto, result);

        boolean descuentoValido = validarDescuento(dto.getDescuento(), result);
        BigDecimal subtotal = validarDetalles(dto.getDetalles(), result);

        if (subtotal != null) {
            validarTotales(subtotal, descuentoValido ? dto.getDescuento() : null, result);
        }

        return result;
    }

    /** Las facturas y notas requieren ARCA: todavía no se emiten. */
    private void validarTipoComprobante(TipoComprobante tipo, ValidationResult result) {

        if (tipo == null) {
            result.addError("tipoComprobante", "Debe seleccionar el tipo de comprobante.");
            return;
        }

        if (!tipo.esDelPuntoDeVenta()) {
            result.addError("tipoComprobante", "No se puede emitir " + tipo.getDescripcion().toLowerCase()
                    + ": requiere facturación electrónica (ARCA), que todavía no está disponible.");
        }
    }

    private void validarFormaPagoYCliente(VentaDto dto, ValidationResult result) {

        ValidationUtils.notNull(dto.getFormaPago(), "formaPago", "Debe seleccionar la forma de pago.", result);

        if (dto.getClienteId() == null) {

            if (dto.getFormaPago() == FormaPago.CUENTA_CORRIENTE) {
                result.addError("clienteId",
                        "Para vender en cuenta corriente debe seleccionar un cliente.");
            }

            return;
        }

        if (clienteRepository.findByIdAndEstadoRegistro(dto.getClienteId(), EstadoRegistro.ACTIVO).isEmpty()) {
            result.addError("clienteId", "El cliente seleccionado no existe o fue dado de baja.");
        }
    }

    /** @return true si el descuento no tiene errores. */
    private boolean validarDescuento(BigDecimal descuento, ValidationResult result) {

        if (descuento == null) {
            result.addError("descuento", "El descuento es obligatorio (puede ser cero).");
            return false;
        }

        if (descuento.signum() < 0) {
            result.addError("descuento", "El descuento no puede ser negativo.");
            return false;
        }

        if (descuento.stripTrailingZeros().scale() > IMPORTE_DECIMALES_MAX) {
            result.addError("descuento",
                    "El descuento no puede tener más de " + IMPORTE_DECIMALES_MAX + " decimales.");
            return false;
        }

        return true;
    }

    /**
     * Valida los renglones contra los productos.
     *
     * @return el subtotal de la venta, o null si algún renglón tiene errores
     */
    private BigDecimal validarDetalles(List<DetalleVentaDto> detalles, ValidationResult result) {

        if (detalles == null || detalles.isEmpty()) {
            result.addError("detalles", "La venta debe tener al menos un producto.");
            return null;
        }

        List<Long> ids = detalles.stream()
                .filter(Objects::nonNull)
                .map(DetalleVentaDto::getProductoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Producto> productos = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        boolean conErrores = false;
        BigDecimal subtotal = BigDecimal.ZERO;

        for (int i = 0; i < detalles.size(); i++) {

            DetalleVentaDto detalle = detalles.get(i);
            String campo = "detalles[" + i + "]";
            String renglon = "Renglón " + (i + 1) + ": ";

            if (detalle == null || detalle.getProductoId() == null) {
                result.addError(campo + ".productoId", renglon + "debe seleccionar un producto.");
                conErrores = true;
                continue;
            }

            Producto producto = productos.get(detalle.getProductoId());

            if (producto == null || producto.getEstadoRegistro() != EstadoRegistro.ACTIVO) {
                result.addError(campo + ".productoId",
                        renglon + "el producto seleccionado no existe o fue dado de baja.");
                conErrores = true;
                continue;
            }

            if (!CantidadRenglonValidacion.validar(
                    detalle.getCantidad(), producto, campo + ".cantidad", renglon, "se vende", result)) {
                conErrores = true;
                continue;
            }

            subtotal = subtotal.add(DetalleVenta.calcularSubtotal(detalle.getCantidad(), producto.getPrecioVenta()));
        }

        return conErrores ? null : subtotal;
    }

    private void validarTotales(BigDecimal subtotal, BigDecimal descuento, ValidationResult result) {

        if (subtotal.compareTo(IMPORTE_MAXIMO) > 0) {
            result.addError("detalles", "El subtotal de la venta (" + CurrencyUtils.format(subtotal)
                    + ") supera el máximo permitido (" + CurrencyUtils.format(IMPORTE_MAXIMO) + ").");
            return;
        }

        if (descuento != null && descuento.compareTo(subtotal) > 0) {
            result.addError("descuento", "El descuento (" + CurrencyUtils.format(descuento)
                    + ") no puede superar el subtotal de la venta (" + CurrencyUtils.format(subtotal) + ").");
        }
    }

}
