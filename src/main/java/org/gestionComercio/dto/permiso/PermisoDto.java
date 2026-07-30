package org.gestionComercio.dto.permiso;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.PermisoCodigo;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PermisoDto extends BaseDto {

    private PermisoCodigo codigo;

    private String descripcion;
}