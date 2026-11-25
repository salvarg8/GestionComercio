package org.gestionComercio.dto.caja;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.TipoMovimientoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista de sólo lectura de un movimiento de caja.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MovimientoCajaDto extends BaseDto {

    private Long cajaId;

    private LocalDateTime fecha;

    private TipoMovimientoCaja tipo;

    private String descripcion;

    /** Siempre positivo; el tipo indica si suma o resta. */
    private BigDecimal importe;

    private Long usuarioId;

    /** Nombre de usuario (login) del responsable. */
    private String usuarioNombre;

    /** Nombre y apellido del responsable. */
    private String usuarioNombreCompleto;

    /** Encargado que confirmó el retiro; null si no hizo falta confirmación. */
    private Long usuarioAutorizacionId;

    private String usuarioAutorizacionNombre;

    private String usuarioAutorizacionNombreCompleto;

}
