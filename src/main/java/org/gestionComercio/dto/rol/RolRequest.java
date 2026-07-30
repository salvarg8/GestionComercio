package org.gestionComercio.dto.rol;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RolRequest {

    private Long id;

    private String nombre;

    private String descripcion;

    private Set<Long> permisoIds;
}