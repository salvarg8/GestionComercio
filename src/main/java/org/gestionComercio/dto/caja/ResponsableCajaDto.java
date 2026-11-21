package org.gestionComercio.dto.caja;

/**
 * Usuario al que se le puede abrir una caja.
 *
 * @param id             id del usuario
 * @param usuario        nombre de usuario (login)
 * @param nombreCompleto nombre y apellido
 */
public record ResponsableCajaDto(Long id, String usuario, String nombreCompleto) {

    /** Texto que se muestra en el selector de responsable. */
    @Override
    public String toString() {
        return nombreCompleto + " (" + usuario + ")";
    }
}
