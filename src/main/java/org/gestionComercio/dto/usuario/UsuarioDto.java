package org.gestionComercio.dto.usuario;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.EstadoUsuario;
import org.gestionComercio.enums.TipoDocumento;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UsuarioDto extends BaseDto {

    private String usuario;

    private String nombre;

    private String apellido;

    private TipoDocumento tipoDocumento;

    private String documento;

    private EstadoUsuario estadoUsuario;

    private Long rolId;

    /** Solo lectura: se completa al convertir desde la entidad. */
    private String rolNombre;

    /**
     * Solo escritura: contraseña en texto plano ingresada en el formulario.
     * Obligatoria al crear; al editar, si viene vacía se conserva la actual.
     * Nunca se completa al leer un usuario.
     */
    private String password;

    public String getNombreCompleto() {
        return apellido + ", " + nombre;
    }
}
