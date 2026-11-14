package org.gestionComercio.dto.marca;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.EstadoRegistro;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class MarcaDto extends BaseDto {

    private String nombre;

    private String descripcion;

    /** Sólo lectura: el estado se cambia con darDeBaja/habilitar, nunca al guardar. */
    private EstadoRegistro estadoRegistro;

    public boolean isActiva() {
        return estadoRegistro == null || estadoRegistro == EstadoRegistro.ACTIVO;
    }
}
