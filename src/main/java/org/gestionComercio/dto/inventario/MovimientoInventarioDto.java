package org.gestionComercio.dto.inventario;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.MotivoAjusteInventario;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.enums.UnidadMedida;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista de sólo lectura de un movimiento de inventario, para historiales y
 * listados. Los movimientos se crean con {@code MovimientoInventario.nuevo()},
 * nunca a partir de este DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MovimientoInventarioDto extends BaseDto {

    private Long productoId;

    private String productoCodigo;

    private String productoNombre;

    private UnidadMedida productoUnidadMedida;

    private TipoMovimientoInventario tipo;

    /** Variación de stock con signo: positiva en entradas, negativa en salidas. */
    private BigDecimal cantidad;

    private BigDecimal stockAnterior;

    private BigDecimal stockResultante;

    private LocalDateTime fecha;

    private String motivo;

    /** Sólo en ajustes. */
    private MotivoAjusteInventario motivoAjuste;

    private OrigenMovimientoInventario origen;

    /** ID de la venta o compra que lo generó; null si es manual. */
    private Long origenId;

    /** Si es la reversión de otro movimiento (anulación), el ID de ese movimiento. */
    private Long movimientoRevertidoId;

    private Long usuarioId;

    /** Nombre de usuario (login) del responsable. */
    private String usuarioNombre;

    /** Nombre y apellido del responsable. */
    private String usuarioNombreCompleto;

}
