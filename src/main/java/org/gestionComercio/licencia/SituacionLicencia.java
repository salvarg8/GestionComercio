package org.gestionComercio.licencia;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Situación de la licencia en esta PC. Las que no permiten operar dejan el
 * sistema en modo sólo consulta: se pueden ver y exportar los datos, pero no
 * registrar operaciones.
 */
@Getter
@RequiredArgsConstructor
public enum SituacionLicencia {

    PRUEBA("Período de prueba", true),
    ACTIVA("Licencia activa", true),
    POR_VENCER("Licencia por vencer", true),
    PRUEBA_VENCIDA("Período de prueba terminado", false),
    VENCIDA("Licencia vencida", false),
    EQUIPO_NO_HABILITADO("Esta PC no está habilitada en la licencia", false),
    INVALIDA("Licencia inválida", false),
    RELOJ_ATRASADO("Fecha de la PC incorrecta", false);

    private final String descripcion;

    private final boolean permiteOperar;

}
