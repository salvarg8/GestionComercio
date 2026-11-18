package org.gestionComercio.dto.inventario;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.TipoMovimientoInventario;

import java.time.LocalDateTime;

/**
 * Vista de sólo lectura de un movimiento de inventario, para historiales y
 * listados. Los movimientos se crean con {@code MovimientoInventario.registrar},
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

    private TipoMovimientoInventario tipo;

    /** Variación de stock con signo: positiva en entradas, negativa en salidas. */
    private Integer cantidad;

    private Integer stockAnterior;

    private Integer stockResultante;

    private LocalDateTime fecha;

    private String motivo;

    private Long usuarioId;

    /** Nombre de usuario (login) del responsable. */
    private String usuarioNombre;

    /** Nombre y apellido del responsable. */
    private String usuarioNombreCompleto;

}
