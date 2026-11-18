package org.gestionComercio.dto.caja;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.EstadoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista de sólo lectura de una caja con su resumen. Las cajas se abren y
 * cierran con {@code CajaService}, nunca a partir de este DTO.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CajaDto extends BaseDto {

    private LocalDateTime fechaApertura;

    /** Null mientras está abierta. */
    private LocalDateTime fechaCierre;

    private EstadoCaja estadoCaja;

    private BigDecimal saldoInicial;

    /** Suma de los movimientos que ingresan dinero (sin contar la apertura). */
    private BigDecimal totalIngresos;

    /** Suma de los movimientos que retiran dinero, en positivo. */
    private BigDecimal totalEgresos;

    /** Saldo inicial + ingresos − egresos. */
    private BigDecimal saldoEsperado;

    /** Saldo contado en el arqueo. Null mientras está abierta. */
    private BigDecimal saldoFinal;

    /** Saldo contado − saldo esperado. Null mientras está abierta. */
    private BigDecimal diferencia;

    private String observacionCierre;

    private Long usuarioId;

    /** Nombre de usuario (login) del responsable. */
    private String usuarioNombre;

    /** Nombre y apellido del responsable. */
    private String usuarioNombreCompleto;

}
