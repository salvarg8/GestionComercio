package org.gestionComercio.validation;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.dto.compra.DetalleCompraDto;
import org.gestionComercio.entity.DetalleCompra;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.enums.EstadoCompra;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.repository.CompraRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.ProveedorRepository;
import org.gestionComercio.util.CurrencyUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Valida una compra nueva ya normalizada por el servicio (número de factura
 * recortado o null, estado y fecha completos, lista de renglones no nula).
 * Informa todos los errores juntos.
 *
 * <p>Los errores de un renglón usan el campo {@code detalles[i].campo}
 * (i desde cero) y su mensaje indica el número de renglón (desde uno).
 */
@Component
@RequiredArgsConstructor
public class CompraValidator implements Validator<CompraDto> {

    /** Largo de la columna numero_factura. */
    public static final int NUMERO_FACTURA_MAX = 50;

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;

    @Override
    public ValidationResult validate(CompraDto dto) {

        ValidationResult result = new ValidationResult();

        boolean proveedorValido = validarProveedor(dto.getProveedorId(), result);
        validarNumeroFactura(dto, proveedorValido, result);

        ValidationUtils.notNull(dto.getFormaPago(), "formaPago", "Debe seleccionar la forma de pago.", result);

        validarEstado(dto.getEstadoCompra(), result);
        validarFecha(dto.getFecha(), result);

        BigDecimal total = validarDetalles(dto.getDetalles(), result);

        if (total != null && total.compareTo(VentaValidator.IMPORTE_MAXIMO) > 0) {
            result.addError("detalles", "El total de la compra (" + CurrencyUtils.format(total)
                    + ") supera el máximo permitido (" + CurrencyUtils.format(VentaValidator.IMPORTE_MAXIMO) + ").");
        }

        return result;
    }

    /** @return true si el proveedor existe y está activo. */
    private boolean validarProveedor(Long proveedorId, ValidationResult result) {

        if (proveedorId == null) {
            result.addError("proveedorId", "Debe seleccionar el proveedor.");
            return false;
        }

        if (proveedorRepository.findByIdAndEstadoRegistro(proveedorId, EstadoRegistro.ACTIVO).isEmpty()) {
            result.addError("proveedorId", "El proveedor seleccionado no existe o fue dado de baja.");
            return false;
        }

        return true;
    }

    private void validarNumeroFactura(CompraDto dto, boolean proveedorValido, ValidationResult result) {

        String numero = dto.getNumeroFactura();

        if (numero == null) {
            return;
        }

        if (numero.length() > NUMERO_FACTURA_MAX) {
            result.addError("numeroFactura",
                    "El número de factura no puede superar los " + NUMERO_FACTURA_MAX + " caracteres.");
            return;
        }

        if (proveedorValido && compraRepository.existeFactura(dto.getProveedorId(), numero)) {
            result.addError("numeroFactura",
                    "Ya se registró una compra con la factura " + numero + " de este proveedor.");
        }
    }

    private void validarEstado(EstadoCompra estado, ValidationResult result) {

        if (estado != EstadoCompra.PENDIENTE && estado != EstadoCompra.RECIBIDA) {
            result.addError("estadoCompra", "Una compra nueva sólo puede quedar pendiente o recibida.");
        }
    }

    private void validarFecha(LocalDateTime fecha, ValidationResult result) {

        if (fecha == null) {
            result.addError("fecha", "Debe indicar la fecha de la compra.");
            return;
        }

        if (fecha.isAfter(LocalDateTime.now())) {
            result.addError("fecha", "La fecha de la compra no puede ser futura.");
        }
    }

    /**
     * Valida los renglones contra los productos.
     *
     * @return el total de la compra, o null si algún renglón tiene errores
     */
    private BigDecimal validarDetalles(List<DetalleCompraDto> detalles, ValidationResult result) {

        if (detalles == null || detalles.isEmpty()) {
            result.addError("detalles", "La compra debe tener al menos un producto.");
            return null;
        }

        List<Long> ids = detalles.stream()
                .filter(Objects::nonNull)
                .map(DetalleCompraDto::getProductoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Producto> productos = productoRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        boolean conErrores = false;
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < detalles.size(); i++) {

            DetalleCompraDto detalle = detalles.get(i);
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

            boolean cantidadValida = CantidadRenglonValidacion.validar(
                    detalle.getCantidad(), producto, campo + ".cantidad", renglon, "se compra", result);
            boolean precioValido = validarPrecio(detalle.getPrecioCompra(), producto, campo, renglon, result);

            if (!cantidadValida || !precioValido) {
                conErrores = true;
                continue;
            }

            total = total.add(DetalleCompra.calcularSubtotal(detalle.getCantidad(), detalle.getPrecioCompra()));
        }

        return conErrores ? null : total;
    }

    /** @return true si el precio de compra no tiene errores. */
    private boolean validarPrecio(
            BigDecimal precio, Producto producto, String campo, String renglon, ValidationResult result) {

        String nombre = "\"" + producto.getNombre() + "\"";
        String campoPrecio = campo + ".precioCompra";

        if (precio == null) {
            result.addError(campoPrecio, renglon + "debe indicar el precio de compra de " + nombre + ".");
            return false;
        }

        if (precio.signum() < 0) {
            result.addError(campoPrecio, renglon + "el precio de compra de " + nombre + " no puede ser negativo.");
            return false;
        }

        if (precio.stripTrailingZeros().scale() > VentaValidator.IMPORTE_DECIMALES_MAX) {
            result.addError(campoPrecio, renglon + "el precio de compra de " + nombre + " no puede tener más de "
                    + VentaValidator.IMPORTE_DECIMALES_MAX + " decimales.");
            return false;
        }

        if (precio.compareTo(VentaValidator.IMPORTE_MAXIMO) > 0) {
            result.addError(campoPrecio, renglon + "el precio de compra de " + nombre + " es demasiado grande.");
            return false;
        }

        return true;
    }

}
