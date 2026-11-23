package org.gestionComercio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gestionComercio.enums.AmbienteArca;

import java.time.Instant;

/**
 * Ticket de acceso del WSAA (token y sign) guardado para reutilizarlo hasta
 * que venza (unas 12 horas). Está en la base para que lo compartan todas las
 * PC: ARCA no entrega otro mientras el anterior siga vigente.
 *
 * <p>No extiende {@link BaseEntity}: es un dato técnico, sin auditoría ni baja lógica.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tickets_acceso_arca")
public class TicketAccesoArca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AmbienteArca ambiente;

    @Column(nullable = false, length = 15)
    private String cuit;

    /** Web service para el que vale el ticket (wsfe). */
    @Column(nullable = false, length = 30)
    private String servicio;

    @Column(nullable = false, columnDefinition = "text")
    private String token;

    @Column(nullable = false, columnDefinition = "text")
    private String sign;

    @Column(nullable = false)
    private Instant generado;

    @Column(nullable = false)
    private Instant vence;

}
