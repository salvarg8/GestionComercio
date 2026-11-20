package org.gestionComercio.controller.compra;

import org.gestionComercio.dto.compra.DetalleCompraDto;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.entity.DetalleCompra;
import org.gestionComercio.util.CantidadUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Renglones del formulario de compra, sin dependencias de JavaFX. Controla
 * cantidad y precio antes de enviarlos; el servicio vuelve a validarlos.
 *
 * <p>Agregar un producto que ya está con el mismo precio suma la cantidad a
 * su renglón; con otro precio se agrega un renglón nuevo (una factura puede
 * traer el mismo producto a dos precios, por ejemplo con una bonificación).
 */
final class RenglonesCompra {

    static final class Renglon {

        private final ProductoDto producto;
        private BigDecimal cantidad;
        private BigDecimal precio;

        private Renglon(ProductoDto producto, BigDecimal cantidad, BigDecimal precio) {
            this.producto = producto;
            this.cantidad = cantidad;
            this.precio = precio;
        }

        ProductoDto getProducto() {
            return producto;
        }

        BigDecimal getCantidad() {
            return cantidad;
        }

        BigDecimal getPrecio() {
            return precio;
        }

        /** Cantidad × precio, redondeado a dos decimales como lo calcula el servicio. */
        BigDecimal getSubtotal() {
            return DetalleCompra.calcularSubtotal(cantidad, precio);
        }

        /** @return true si el precio difiere del precio de compra actual del producto. */
        boolean cambiaPrecio() {
            BigDecimal actual = producto.getPrecioCompra();
            return actual == null || actual.compareTo(precio) != 0;
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
     * Agrega el producto o suma la cantidad al renglón con el mismo precio.
     *
     * @return el renglón agregado o modificado
     * @throws IllegalArgumentException si la cantidad (o la suma) o el precio no son válidos
     */
    Renglon agregar(ProductoDto producto, BigDecimal cantidad, BigDecimal precio) {

        Objects.requireNonNull(producto, "El producto es obligatorio.");

        requerir(errorPrecio(precio));
        requerir(errorCantidad(producto, cantidad));

        Renglon existente = renglones.stream()
                .filter(r -> Objects.equals(r.producto.getId(), producto.getId()) && r.precio.compareTo(precio) == 0)
                .findFirst()
                .orElse(null);

        if (existente != null) {
            BigDecimal suma = existente.cantidad.add(cantidad);
            requerir(errorCantidad(producto, suma));
            existente.cantidad = suma;
            return existente;
        }

        Renglon renglon = new Renglon(producto, cantidad, precio);
        renglones.add(renglon);
        return renglon;
    }

    /** @throws IllegalArgumentException si la cantidad no es válida para el producto */
    void cambiarCantidad(Renglon renglon, BigDecimal cantidad) {
        requerir(errorCantidad(renglon.producto, cantidad));
        renglon.cantidad = cantidad;
    }

    /** @throws IllegalArgumentException si el precio no es válido */
    void cambiarPrecio(Renglon renglon, BigDecimal precio) {
        requerir(errorPrecio(precio));
        renglon.precio = precio;
    }

    void quitar(Renglon renglon) {
        renglones.remove(renglon);
    }

    void vaciar() {
        renglones.clear();
    }

    /** Suma de los subtotales de los renglones. */
    BigDecimal total() {
        return renglones.stream()
                .map(Renglon::getSubtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    /** @return true si algún renglón tiene un precio distinto del precio de compra actual del producto. */
    boolean hayCambiosDePrecio() {
        return renglones.stream().anyMatch(Renglon::cambiaPrecio);
    }

    /** Renglones para {@code CompraService}: producto, cantidad y precio. */
    List<DetalleCompraDto> toDetalles() {
        return renglones.stream()
                .<DetalleCompraDto>map(r -> DetalleCompraDto.builder()
                        .productoId(r.producto.getId())
                        .cantidad(r.cantidad)
                        .precioCompra(r.precio)
                        .build())
                .toList();
    }

    // ------------------------------------------------------------------
    // Reglas
    // ------------------------------------------------------------------

    /**
     * Mayor que cero, hasta tres decimales y entera si la unidad no es fraccionable.
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
            return "\"" + producto.getNombre() + "\" se compra por "
                    + producto.getUnidadMedida().getDescripcion().toLowerCase()
                    + ": la cantidad debe ser un número entero.";
        }

        return null;
    }

    /** @return el mensaje de error, o null si el precio es válido (cero o más, hasta dos decimales). */
    static String errorPrecio(BigDecimal precio) {

        if (precio == null) {
            return "Ingrese el costo.";
        }

        if (precio.signum() < 0) {
            return "El costo no puede ser negativo.";
        }

        if (precio.stripTrailingZeros().scale() > 2) {
            return "El costo admite hasta dos decimales.";
        }

        if (precio.compareTo(CompraFormato.IMPORTE_MAXIMO) > 0) {
            return "El costo supera el máximo permitido.";
        }

        return null;
    }

    private static void requerir(String error) {
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }
}
