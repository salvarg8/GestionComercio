package org.gestionComercio.controller.venta;

import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.entity.DetalleVenta;
import org.gestionComercio.util.CantidadUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Carrito del punto de venta: los renglones que se van cargando, con sus
 * subtotales y el total. No depende de JavaFX, así que se puede probar sin
 * abrir ventanas.
 *
 * <p>Los precios y el stock son los del producto al agregarlo; el precio
 * definitivo y el control de stock los hace {@code VentaService} al confirmar.
 * Agregar un producto que ya está en el carrito suma la cantidad a su renglón.
 */
class CarritoVenta {

    /** Un renglón del carrito. */
    static final class Renglon {

        private final ProductoDto producto;
        private BigDecimal cantidad;

        private Renglon(ProductoDto producto, BigDecimal cantidad) {
            this.producto = producto;
            this.cantidad = cantidad;
        }

        ProductoDto getProducto() {
            return producto;
        }

        BigDecimal getCantidad() {
            return cantidad;
        }

        BigDecimal getPrecioUnitario() {
            return Objects.requireNonNullElse(producto.getPrecioVenta(), BigDecimal.ZERO);
        }

        /** Cantidad × precio, redondeado a dos decimales como lo calcula el servicio. */
        BigDecimal getSubtotal() {
            return DetalleVenta.calcularSubtotal(cantidad, getPrecioUnitario());
        }

        /** @return true si el producto controla stock, no admite negativo y la cantidad supera lo disponible. */
        boolean superaStock() {
            return Boolean.TRUE.equals(producto.getManejaStock())
                    && !Boolean.TRUE.equals(producto.getPermiteStockNegativo())
                    && cantidad.compareTo(Objects.requireNonNullElse(producto.getStock(), BigDecimal.ZERO)) > 0;
        }
    }

    private final List<Renglon> renglones = new ArrayList<>();

    List<Renglon> getRenglones() {
        return Collections.unmodifiableList(renglones);
    }

    boolean estaVacio() {
        return renglones.isEmpty();
    }

    /**
     * Agrega el producto o suma la cantidad a su renglón.
     *
     * @return el renglón agregado o modificado
     * @throws IllegalArgumentException si la cantidad (o la suma resultante) no es válida
     */
    Renglon agregar(ProductoDto producto, BigDecimal cantidad) {

        Objects.requireNonNull(producto, "El producto es obligatorio.");

        Renglon existente = buscar(producto.getId());
        BigDecimal nueva = existente == null ? cantidad : existente.cantidad.add(requerirCantidad(cantidad));

        validar(producto, nueva);

        if (existente != null) {
            existente.cantidad = nueva;
            return existente;
        }

        Renglon renglon = new Renglon(producto, cantidad);
        renglones.add(renglon);
        return renglon;
    }

    /** @throws IllegalArgumentException si la cantidad no es válida para el producto */
    void cambiarCantidad(Renglon renglon, BigDecimal cantidad) {
        validar(renglon.producto, cantidad);
        renglon.cantidad = cantidad;
    }

    /**
     * Suma (o resta, con un valor negativo) a la cantidad del renglón. Si la
     * cantidad quedaría en cero o menos, no cambia nada.
     *
     * @return true si cambió la cantidad
     */
    boolean sumar(Renglon renglon, BigDecimal diferencia) {

        BigDecimal nueva = renglon.cantidad.add(diferencia);

        if (nueva.signum() <= 0) {
            return false;
        }

        renglon.cantidad = nueva;
        return true;
    }

    void quitar(Renglon renglon) {
        renglones.remove(renglon);
    }

    void vaciar() {
        renglones.clear();
    }

    /** Suma de los subtotales de los renglones. */
    BigDecimal subtotal() {
        return renglones.stream()
                .map(Renglon::getSubtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** Subtotal − descuento (un descuento null se toma como cero). */
    BigDecimal total(BigDecimal descuento) {
        return subtotal().subtract(Objects.requireNonNullElse(descuento, BigDecimal.ZERO));
    }

    /** Renglones cuya cantidad supera el stock que tenía el producto al agregarlo. */
    List<Renglon> sinStockSuficiente() {
        return renglones.stream().filter(Renglon::superaStock).toList();
    }

    /** Renglones para {@code VentaService}: sólo producto y cantidad. */
    List<DetalleVentaDto> toDetalles() {
        return renglones.stream()
                .<DetalleVentaDto>map(r -> DetalleVentaDto.builder()
                        .productoId(r.producto.getId())
                        .cantidad(r.cantidad)
                        .build())
                .toList();
    }

    // ------------------------------------------------------------------
    // Reglas
    // ------------------------------------------------------------------

    /**
     * Valida una cantidad para un producto: mayor que cero, hasta tres
     * decimales y entera si la unidad no es fraccionable.
     *
     * @return el mensaje de error, o null si es válida
     */
    static String errorCantidad(ProductoDto producto, BigDecimal cantidad) {

        if (cantidad == null) {
            return "Ingrese la cantidad.";
        }

        if (cantidad.signum() <= 0) {
            return "La cantidad debe ser mayor que cero.";
        }

        if (!CantidadUtils.escalaValida(cantidad)) {
            return "La cantidad admite como máximo " + CantidadUtils.ESCALA + " decimales.";
        }

        if (!CantidadUtils.dentroDelMaximo(cantidad)) {
            return "La cantidad es demasiado grande.";
        }

        if (producto.getUnidadMedida() != null
                && !producto.getUnidadMedida().isFraccionable()
                && !CantidadUtils.esEntera(cantidad)) {
            return "\"" + producto.getNombre() + "\" se vende por "
                    + producto.getUnidadMedida().getDescripcion().toLowerCase()
                    + ": la cantidad debe ser un número entero.";
        }

        return null;
    }

    /**
     * Resultado de calcular el cobro con lo que escribió el usuario.
     *
     * @param descuento importe del descuento (cero si no se indicó); null si no es válido
     * @param total     subtotal − descuento
     * @param pagaCon   efectivo recibido; null si no se indicó o no es efectivo
     * @param vuelto    pagaCon − total; null si no se indicó con cuánto paga
     * @param error     mensaje para el usuario; null si se puede confirmar
     */
    record Cobro(BigDecimal descuento, BigDecimal total, BigDecimal pagaCon, BigDecimal vuelto, String error) {

        boolean valido() {
            return error == null;
        }
    }

    /**
     * Calcula descuento, total y vuelto a partir de los textos ingresados.
     * Con efectivo, "paga con" es opcional (vacío = paga justo) pero no puede
     * ser menor que el total.
     */
    static Cobro cobrar(BigDecimal subtotal, String descuentoTexto, boolean efectivo, String pagaConTexto) {

        BigDecimal descuento;

        try {
            descuento = Objects.requireNonNullElse(
                    VentaFormato.leerImporte(descuentoTexto, "El descuento"), BigDecimal.ZERO);
        } catch (IllegalArgumentException ex) {
            return new Cobro(null, subtotal, null, null, ex.getMessage());
        }

        if (descuento.compareTo(subtotal) > 0) {
            return new Cobro(null, subtotal, null, null, "El descuento (" + VentaFormato.importe(descuento)
                    + ") no puede superar el subtotal (" + VentaFormato.importe(subtotal) + ").");
        }

        BigDecimal total = subtotal.subtract(descuento);

        if (!efectivo) {
            return new Cobro(descuento, total, null, null, null);
        }

        BigDecimal pagaCon;

        try {
            pagaCon = VentaFormato.leerImporte(pagaConTexto, "El importe recibido");
        } catch (IllegalArgumentException ex) {
            return new Cobro(descuento, total, null, null, ex.getMessage());
        }

        if (pagaCon == null) {
            return new Cobro(descuento, total, null, null, null);
        }

        BigDecimal vuelto = pagaCon.subtract(total);

        return new Cobro(descuento, total, pagaCon, vuelto, vuelto.signum() < 0
                ? "El importe recibido es menor que el total: faltan " + VentaFormato.importe(vuelto.negate()) + "."
                : null);
    }

    private Renglon buscar(Long productoId) {
        return renglones.stream()
                .filter(r -> Objects.equals(r.producto.getId(), productoId))
                .findFirst()
                .orElse(null);
    }

    private static BigDecimal requerirCantidad(BigDecimal cantidad) {

        if (cantidad == null) {
            throw new IllegalArgumentException("Ingrese la cantidad.");
        }

        return cantidad;
    }

    private static void validar(ProductoDto producto, BigDecimal cantidad) {

        String error = errorCantidad(producto, cantidad);

        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }
}
