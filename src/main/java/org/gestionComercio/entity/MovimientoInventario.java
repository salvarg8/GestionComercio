package org.gestionComercio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gestionComercio.enums.TipoMovimientoInventario;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Registro histórico de una variación de stock de un producto.
 *
 * <p>La {@code cantidad} es la variación con signo: positiva en una entrada,
 * negativa en una salida y de cualquier signo (distinta de cero) en un ajuste.
 * Siempre se cumple {@code stockResultante = stockAnterior + cantidad}.
 *
 * <p>Los movimientos son inmutables: se crean con {@link #registrar} y nunca se
 * modifican ni se borran. Un error se corrige con un movimiento nuevo (por
 * ejemplo, un ajuste en sentido contrario), así el historial queda completo.
 * Por eso no hay setters, las columnas no son actualizables y cualquier intento
 * de actualizar o borrar el registro desde JPA lanza una excepción.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "movimientos_inventario",
        indexes = {
                @Index(name = "idx_movimiento_inventario_producto_fecha", columnList = "producto_id, fecha"),
                @Index(name = "idx_movimiento_inventario_fecha", columnList = "fecha")
        }
)
public class MovimientoInventario extends BaseEntity {

    public static final int MOTIVO_MAX = 255;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TipoMovimientoInventario tipo;

    /** Variación de stock con signo. */
    @Column(nullable = false, updatable = false)
    private Integer cantidad;

    @Column(nullable = false, updatable = false)
    private Integer stockAnterior;

    @Column(nullable = false, updatable = false)
    private Integer stockResultante;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(nullable = false, updatable = false, length = MOTIVO_MAX)
    private String motivo;

    /** Usuario responsable del movimiento. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    /**
     * Crea el movimiento a partir del stock actual del producto. No modifica el
     * producto: quien registra el movimiento debe actualizar su stock con
     * {@link #getStockResultante()} en la misma transacción.
     *
     * @param cantidad variación con signo (ver {@link TipoMovimientoInventario#admiteCantidad(int)})
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public static MovimientoInventario registrar(
            Producto producto,
            TipoMovimientoInventario tipo,
            int cantidad,
            String motivo,
            Usuario usuario) {

        Objects.requireNonNull(producto, "El producto es obligatorio.");

        int stockAnterior = producto.getStock() == null ? 0 : producto.getStock();

        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.producto = producto;
        movimiento.tipo = tipo;
        movimiento.cantidad = cantidad;
        movimiento.stockAnterior = stockAnterior;
        movimiento.stockResultante = Math.addExact(stockAnterior, cantidad);
        movimiento.fecha = LocalDateTime.now();
        movimiento.motivo = motivo == null ? null : motivo.trim();
        movimiento.usuario = usuario;

        movimiento.verificarConsistencia();

        return movimiento;
    }

    /**
     * Última defensa antes de insertar: garantiza que no se guarde un movimiento
     * incompleto o con los stocks desalineados.
     */
    @PrePersist
    void verificarConsistencia() {

        requerir(producto != null, "El producto es obligatorio.");
        requerir(usuario != null, "El usuario responsable es obligatorio.");
        requerir(tipo != null, "El tipo de movimiento es obligatorio.");
        requerir(fecha != null, "La fecha es obligatoria.");
        requerir(cantidad != null && stockAnterior != null && stockResultante != null,
                "La cantidad y los stocks anterior y resultante son obligatorios.");

        requerir(tipo.admiteCantidad(cantidad), switch (tipo) {
            case ENTRADA -> "La cantidad de una entrada debe ser positiva.";
            case SALIDA -> "La cantidad de una salida debe ser negativa.";
            case AJUSTE -> "La cantidad de un ajuste no puede ser cero.";
        });

        requerir((long) stockAnterior + cantidad == stockResultante,
                "El stock resultante debe ser el stock anterior más la cantidad.");

        requerir(motivo != null && !motivo.isBlank(), "El motivo es obligatorio.");
        requerir(motivo.length() <= MOTIVO_MAX,
                "El motivo no puede superar los " + MOTIVO_MAX + " caracteres.");
    }

    @PreUpdate
    void impedirModificacion() {
        throw new IllegalStateException(
                "Los movimientos de inventario no se pueden modificar. Registre un ajuste para corregirlo.");
    }

    @PreRemove
    void impedirBorrado() {
        throw new IllegalStateException(
                "Los movimientos de inventario no se pueden eliminar. Registre un ajuste para corregirlo.");
    }

    private static void requerir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new IllegalArgumentException(mensaje);
        }
    }

}
