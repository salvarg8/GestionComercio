package org.gestionComercio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Los proveedores no se borran: se dan de baja con {@code estadoRegistro}
 * (ver {@link BaseEntity}) y se pueden volver a habilitar.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "proveedores")
public class Proveedor extends BaseEntity {

    /** Razón social o nombre del proveedor. */
    @Column(nullable = false, length = 150)
    private String razonSocial;

    /**
     * Opcional. Se guarda sólo con los 11 dígitos, sin guiones. Es UNIQUE en la
     * base; SQLite admite varios NULL en una columna UNIQUE.
     */
    @Column(unique = true, length = 11)
    private String cuit;

    @Column(length = 50)
    private String telefono;

    @Column(length = 100)
    private String email;

    @Column(length = 150)
    private String direccion;

    @Column(length = 500)
    private String observaciones;

    @Builder.Default
    @OneToMany(mappedBy = "proveedor")
    private List<Producto> productos = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "proveedor")
    private List<Compra> compras = new ArrayList<>();

}
