package org.gestionComercio.dto.proveedor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.util.CuitUtils;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ProveedorDto extends BaseDto {

    private String razonSocial;

    /** Opcional. Al guardar se normaliza a 11 dígitos sin guiones. */
    private String cuit;

    private String telefono;

    private String email;

    private String direccion;

    private String observaciones;

    /** Sólo lectura: el estado se cambia con darDeBaja/habilitar, nunca al guardar. */
    private EstadoRegistro estadoRegistro;

    public boolean isActivo() {
        return estadoRegistro == null || estadoRegistro == EstadoRegistro.ACTIVO;
    }

    /** CUIT con guiones (XX-XXXXXXXX-X) para mostrar; vacío si no tiene. */
    public String getCuitFormateado() {
        return CuitUtils.formatear(cuit);
    }
}
