package org.gestionComercio.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.enums.CondicionIVA;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "empresas")
public class Empresa extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String razonSocial;

    @Column(length = 150)
    private String nombreFantasia;

    @Column(nullable = false, unique = true, length = 15)
    private String cuit;

    @Column(length = 150)
    private String direccion;

    @Column(length = 50)
    private String telefono;

    @Column(length = 100)
    private String email;

    @Column(length = 255)
    private String logo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CondicionIVA condicionIva;

    @Column(length = 30)
    private String ingresosBrutos;

    @OneToMany(mappedBy = "empresa")
    @Builder.Default
    private List<Usuario> usuarios = new ArrayList<>();

}