package org.gestionComercio.entity;

import jakarta.persistence.*;
import lombok.*;
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

    @Column(precision = 12, scale = 2)
    private BigDecimal saldoFinal;

    @Enumerated(EnumType.STRING)
    @Column(nullable =false)
    private EstadoCaja estadoCaja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Builder.Default
    @OneToMany(mappedBy = "caja")
    private List<Venta> ventas = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "caja")
    private List<MovimientoCaja> movimientos = new ArrayList<>();

}