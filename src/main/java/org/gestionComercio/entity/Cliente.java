package org.gestionComercio.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.TipoDocumento;

import java.util.ArrayList;
import java.util.List;

/**
 * Los clientes se dan de baja con {@code estadoRegistro} (ver {@link BaseEntity})
 * y se pueden volver a habilitar; nunca se borran de la base.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(
        name = "clientes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_clientes_documento",
                columnNames = {"tipo_documento", "numero_documento"}
        )
)
public class Cliente extends BaseEntity {

    /** Nombre de la persona, o razón social si es una empresa. */
    @Column(nullable = false, length = 150)
    private String nombre;

    /** Apellido de la persona; vacío para empresas (razón social en {@link #nombre}). */
    @Column(length = 100)
    private String apellido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoDocumento tipoDocumento;

    /** Sin guiones, puntos ni espacios. Único junto con el tipo de documento. */
    @Column(nullable = false, length = 20)
    private String numeroDocumento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CondicionIVA condicionIva = CondicionIVA.CONSUMIDOR_FINAL;

    @Column(length = 150)
    private String direccion;

    @Column(length = 50)
    private String telefono;

    @Column(length = 100)
    private String email;

    @Builder.Default
    @OneToMany(mappedBy = "cliente")
    private List<Venta> ventas = new ArrayList<>();
}
