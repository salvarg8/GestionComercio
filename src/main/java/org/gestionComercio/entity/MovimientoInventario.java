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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.gestionComercio.enums.MotivoAjusteInventario;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.util.CantidadUtils;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Registro histórico de una variación de stock de un producto.
 *
 * <p>La {@code cantidad} es la variación con signo: positiva en una entrada,
 * negativa en una salida y de cualquier signo (distinta de cero) en un ajuste.
 * Siempre se cumple {@code stockResultante = stockAnterior + cantidad}. Las
 * cantidades admiten hasta {@value CantidadUtils#ESCALA} decimales, pero sólo
 * si la unidad del producto es fraccionable (kilo, litro, metro).
 *
 * <p>Cada movimiento indica su origen: manual, o el comprobante (venta o
 * compra) que lo generó, con su ID. Al anular un comprobante se registra un
 * movimiento opuesto por cada uno de los suyos ({@link #revertir}), que apunta
 * al original en {@code movimientoRevertido}.
 *
 * <p>Los movimientos son inmutables: se crean con {@link #nuevo()} o
 * {@link #revertir} y nunca se modifican ni se borran. Un error se corrige con
 * un movimiento nuevo, así el historial queda completo. Por eso no hay setters,
 * las columnas no son actualizables y cualquier intento de actualizar o borrar
 * el registro desde JPA lanza una excepción.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "movimientos_inventario",
        indexes = {
                @Index(name = "idx_movimiento_inventario_producto_fecha", columnList = "producto_id, fecha"),
                @Index(name = "idx_movimiento_inventario_fecha", columnList = "fecha"),
                @Index(name = "idx_movimiento_inventario_origen", columnList = "origen, origen_id")
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
    @Column(nullable = false, updatable = false, precision = CantidadUtils.PRECISION, scale = CantidadUtils.ESCALA)
    private BigDecimal cantidad;

    @Column(nullable = false, updatable = false, precision = CantidadUtils.PRECISION, scale = CantidadUtils.ESCALA)
    private BigDecimal stockAnterior;

    @Column(nullable = false, updatable = false, precision = CantidadUtils.PRECISION, scale = CantidadUtils.ESCALA)
    private BigDecimal stockResultante;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    /** Descripción libre del movimiento. */
    @Column(nullable = false, updatable = false, length = MOTIVO_MAX)
    private String motivo;

    /** Causa de un ajuste, para agruparlos en reportes. Sólo en ajustes. */
    @Enumerated(EnumType.STRING)
    @Column(updatable = false, length = 30)
    private MotivoAjusteInventario motivoAjuste;

    /* El default permite agregar la columna NOT NULL en una base existente. */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'MANUAL'")
    @Column(nullable = false, updatable = false, length = 20)
    private OrigenMovimientoInventario origen;

    /** ID del comprobante (venta o compra) que generó el movimiento; null si es manual. */
    @Column(name = "origen_id", updatable = false)
    private Long origenId;

    /** Movimiento que éste revierte (por anulación del comprobante); null si es un movimiento original. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_revertido_id", updatable = false)
    private MovimientoInventario movimientoRevertido;

    /** Usuario responsable del movimiento. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    /**
     * Crea el movimiento a partir del stock actual del producto. No modifica el
     * producto: quien registra el movimiento debe aplicarlo con
     * {@link Producto#aplicarMovimiento} en la misma transacción.
     *
     * <p>Uso: {@code MovimientoInventario.nuevo().producto(p).tipo(t).cantidad(c)
     * .motivo(m).usuario(u).registrar()}. El origen por defecto es
     * {@link OrigenMovimientoInventario#MANUAL}; los ajustes exigen
     * {@code motivoAjuste}.
     *
     * @throws IllegalArgumentException si algún dato no es válido
     */
    @Builder(builderMethodName = "nuevo", buildMethodName = "registrar")
    private static MovimientoInventario crear(
            Producto producto,
            TipoMovimientoInventario tipo,
            BigDecimal cantidad,
            String motivo,
            MotivoAjusteInventario motivoAjuste,
            OrigenMovimientoInventario origen,
            Long origenId,
            Usuario usuario) {

        Objects.requireNonNull(producto, "El producto es obligatorio.");
        requerir(cantidad != null, "La cantidad es obligatoria.");
        requerir(CantidadUtils.escalaValida(cantidad),
                "La cantidad admite como máximo " + CantidadUtils.ESCALA + " decimales.");

        UnidadMedida unidad = producto.getUnidadMedida();
        requerir(unidad == null || unidad.isFraccionable() || CantidadUtils.esEntera(cantidad),
                "El producto \"" + producto.getNombre() + "\" se mide en "
                        + (unidad == null ? "" : unidad.getDescripcion().toLowerCase())
                        + " y no admite cantidades con decimales.");

        return armar(producto, tipo, cantidad, motivo, motivoAjuste,
                origen == null ? OrigenMovimientoInventario.MANUAL : origen, origenId, null, usuario);
    }

    /**
     * Crea el movimiento que deshace a {@code original}: mismo producto, origen
     * y comprobante, cantidad opuesta. No controla si la unidad del producto
     * admite decimales, porque la cantidad es la que ya se registró.
     *
     * @throws IllegalArgumentException si algún dato no es válido
     */
    public static MovimientoInventario revertir(MovimientoInventario original, String motivo, Usuario usuario) {

        Objects.requireNonNull(original, "El movimiento a revertir es obligatorio.");
        requerir(original.movimientoRevertido == null, "No se puede revertir una reversión.");

        return armar(original.producto, original.tipo.opuesto(), original.cantidad.negate(), motivo,
                original.motivoAjuste, original.origen, original.origenId, original, usuario);
    }

    private static MovimientoInventario armar(
            Producto producto,
            TipoMovimientoInventario tipo,
            BigDecimal cantidad,
            String motivo,
            MotivoAjusteInventario motivoAjuste,
            OrigenMovimientoInventario origen,
            Long origenId,
            MovimientoInventario revertido,
            Usuario usuario) {

        Objects.requireNonNull(producto, "El producto es obligatorio.");

        BigDecimal stockAnterior = CantidadUtils.normalizar(producto.getStock());

        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.producto = producto;
        movimiento.tipo = tipo;
        movimiento.cantidad = CantidadUtils.normalizar(cantidad);
        movimiento.stockAnterior = stockAnterior;
        movimiento.stockResultante = stockAnterior.add(movimiento.cantidad);
        movimiento.fecha = LocalDateTime.now();
        movimiento.motivo = motivo == null ? null : motivo.trim();
        movimiento.motivoAjuste = motivoAjuste;
        movimiento.origen = origen;
        movimiento.origenId = origenId;
        movimiento.movimientoRevertido = revertido;
        movimiento.usuario = usuario;

        movimiento.verificarConsistencia();

        return movimiento;
    }

    /** @return true si este movimiento deshace a otro. */
    public boolean esReversion() {
        return movimientoRevertido != null;
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

        requerir(CantidadUtils.dentroDelMaximo(cantidad) && CantidadUtils.dentroDelMaximo(stockResultante),
                "La cantidad supera el máximo admitido.");

        requerir(stockAnterior.add(cantidad).compareTo(stockResultante) == 0,
                "El stock resultante debe ser el stock anterior más la cantidad.");

        requerir(motivo != null && !motivo.isBlank(), "El motivo es obligatorio.");
        requerir(motivo.length() <= MOTIVO_MAX,
                "El motivo no puede superar los " + MOTIVO_MAX + " caracteres.");

        requerir((tipo == TipoMovimientoInventario.AJUSTE) == (motivoAjuste != null),
                tipo == TipoMovimientoInventario.AJUSTE
                        ? "Debe indicar la causa del ajuste."
                        : "Sólo los ajustes indican una causa de ajuste.");

        requerir(origen != null, "El origen del movimiento es obligatorio.");
        requerir(origen.requiereComprobante() == (origenId != null),
                origen.requiereComprobante()
                        ? "Un movimiento de " + origen.getDescripcion().toLowerCase() + " debe indicar el comprobante."
                        : "Un movimiento manual no tiene comprobante.");

        if (movimientoRevertido != null) {
            requerir(movimientoRevertido.producto == producto
                            && movimientoRevertido.origen == origen
                            && Objects.equals(movimientoRevertido.origenId, origenId)
                            && movimientoRevertido.cantidad.compareTo(cantidad.negate()) == 0,
                    "La reversión debe ser del mismo producto y comprobante, con la cantidad opuesta.");
        }
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
