package org.gestionComercio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.enums.ComprobantePorDefecto;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.FormatoTicket;
import org.gestionComercio.enums.Redondeo;
import org.gestionComercio.enums.UnidadMedida;

/**
 * Parámetros generales del sistema, compartidos por todas las PC. Hay una
 * sola fila, creada por la migración V9; los valores por defecto de cada
 * campo son los que tenía el sistema antes de que fueran configurables.
 *
 * <p>La impresora no está acá: es de cada PC (ver {@code ImpresoraLocal}).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "configuracion")
public class Configuracion extends BaseEntity {

    /** Si para vender hace falta una caja abierta; si no, la venta queda sin caja. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean ventaExigeCaja = true;

    /** Valor que se propone en "permite stock negativo" al crear un producto. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean stockNegativoPorDefecto = false;

    /** Unidad de medida que se propone al crear un producto. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private UnidadMedida unidadMedidaPorDefecto = UnidadMedida.UNIDAD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private FormaPago formaPagoPorDefecto = FormaPago.EFECTIVO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private ComprobantePorDefecto comprobantePorDefecto = ComprobantePorDefecto.FACTURA;

    /** Redondeo del total de las ventas (hacia abajo). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Redondeo redondeo = Redondeo.SIN_REDONDEO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private FormatoTicket formatoTicket = FormatoTicket.porDefecto();

    /** Si el logo de la empresa se imprime en los comprobantes. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean imprimirLogo = true;

    /** Texto al pie de los comprobantes: horario, redes, políticas de cambio. */
    @Column(length = 500)
    private String pieComprobante;

}
