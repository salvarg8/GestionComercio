package org.gestionComercio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Licencia del comercio, compartida por todas las PC. Hay una sola fila
 * ({@link #ID}), creada por la migración con el inicio del período de prueba.
 *
 * <p>No extiende {@link BaseEntity}: es un dato técnico, sin auditoría ni baja lógica.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "licencia")
public class RegistroLicencia {

    public static final long ID = 1L;

    @Id
    private Long id;

    /** Clave de licencia firmada; null mientras dura el período de prueba. */
    @Column(columnDefinition = "text")
    private String clave;

    @Column(nullable = false)
    private LocalDate inicioPrueba;

    /** Fecha más reciente en que se inició sesión en alguna PC. */
    @Column(nullable = false)
    private LocalDate ultimaFecha;

    private LocalDateTime fechaActivacion;

}
