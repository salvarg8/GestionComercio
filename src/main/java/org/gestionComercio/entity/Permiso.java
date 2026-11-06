package org.gestionComercio.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.entity.converter.PermisoCodigoConverter;
import org.gestionComercio.enums.PermisoCodigo;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "permisos")
public class Permiso extends BaseEntity {

    @Convert(converter = PermisoCodigoConverter.class)
    @Column(nullable = false, unique = true, length = 100)
    private PermisoCodigo codigo;

    @Column(nullable = false, length = 150)
    private String descripcion;
}