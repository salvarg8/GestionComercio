package org.gestionComercio.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.enums.EstadoCaja;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "cajas")
public class Caja extends BaseEntity {

    @Column(nullable = false)
    private LocalDateTime fechaApertura;

    private LocalDateTime fechaCierre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoInicial;

    /** Saldo contado en el arqueo al cerrar. */
    @Column(precision = 12, scale = 2)
    private BigDecimal saldoFinal;

    /** Saldo según el sistema al cerrar: saldo inicial + ingresos − egresos. */
    @Column(precision = 12, scale = 2)
    private BigDecimal saldoEsperado;

    /** Saldo contado − saldo esperado: positiva si sobra, negativa si falta. */
    @Column(precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Column(length = 255)
    private String observacionCierre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoCaja estadoCaja;

    /** Responsable de la caja: el usuario que vende y registra movimientos con ella. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Usuario que abrió la caja; puede ser distinto del responsable. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_apertura_id", nullable = false)
    private Usuario usuarioApertura;

    /** Usuario que cerró la caja; puede ser distinto del responsable. Null mientras está abierta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_cierre_id")
    private Usuario usuarioCierre;

    @Builder.Default
    @OneToMany(mappedBy = "caja")
    private List<Venta> ventas = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "caja")
    private List<MovimientoCaja> movimientos = new ArrayList<>();

}