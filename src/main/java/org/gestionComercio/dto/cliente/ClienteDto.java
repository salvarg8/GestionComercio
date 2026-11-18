package org.gestionComercio.dto.cliente;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.util.CuitUtils;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ClienteDto extends BaseDto {

    /** Nombre de la persona, o razón social si es una empresa. */
    private String nombre;

    /** Obligatorio salvo para clientes con CUIT (empresas). */
    private String apellido;

    private TipoDocumento tipoDocumento;

    /** Al guardar se normaliza: sin guiones, puntos ni espacios. */
    private String numeroDocumento;

    private CondicionIVA condicionIva;

    private String direccion;

    private String telefono;

    private String email;

    /** Sólo lectura: el estado se cambia con darDeBaja/habilitar, nunca al guardar. */
    private EstadoRegistro estadoRegistro;

    public boolean isActivo() {
        return estadoRegistro == null || estadoRegistro == EstadoRegistro.ACTIVO;
    }

    /** "Apellido, Nombre" para personas; la razón social para empresas. */
    public String getNombreCompleto() {

        String nombreTexto = nombre == null ? "" : nombre.trim();

        if (apellido == null || apellido.isBlank()) {
            return nombreTexto;
        }

        return nombreTexto.isEmpty()
                ? apellido.trim()
                : apellido.trim() + ", " + nombreTexto;
    }

    /** CUIT/CUIL con guiones (XX-XXXXXXXX-X); los demás documentos, tal cual. */
    public String getDocumentoFormateado() {

        if (numeroDocumento == null) {
            return "";
        }

        return tipoDocumento == TipoDocumento.CUIT || tipoDocumento == TipoDocumento.CUIL
                ? CuitUtils.formatear(numeroDocumento)
                : numeroDocumento;
    }
}
