package org.gestionComercio.dto.categoria;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class CategoriaDto extends BaseDto {

    private String nombre;

    private String descripcion;
}
