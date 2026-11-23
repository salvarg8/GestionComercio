package org.gestionComercio.licencia;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Estado de la licencia en esta PC en un momento dado.
 *
 * @param situacion     situación (define si se puede operar)
 * @param licencia      licencia activada, o null en el período de prueba o si es inválida
 * @param codigoEquipo  código de esta PC
 * @param finPrueba     primer día sin período de prueba (sólo sin licencia)
 * @param diasRestantes días hasta el fin de la prueba o el vencimiento; null si no aplica
 * @param fecha         fecha de esta PC al evaluar
 * @param detalle       motivo, si la licencia es inválida o el reloj está atrasado
 */
public record EstadoLicencia(SituacionLicencia situacion,
                             Licencia licencia,
                             String codigoEquipo,
                             LocalDate finPrueba,
                             Long diasRestantes,
                             LocalDate fecha,
                             String detalle) {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String SOLO_CONSULTA = " El sistema quedó en modo sólo consulta: se pueden ver y "
            + "exportar los datos, pero no registrar operaciones.";

    public boolean permiteOperar() {
        return situacion.isPermiteOperar();
    }

    /** @return true si hay que avisarle al usuario (todo salvo una licencia activa sin novedades). */
    public boolean requiereAviso() {
        return situacion != SituacionLicencia.ACTIVA;
    }

    /** En el período de prueba están todos los módulos; con licencia, los que incluye. */
    public boolean tieneModulo(ModuloLicencia modulo) {
        if (!permiteOperar()) {
            return false;
        }
        return situacion == SituacionLicencia.PRUEBA || licencia != null && licencia.tieneModulo(modulo);
    }

    /** Mensaje para el usuario. */
    public String mensaje() {
        return switch (situacion) {
            case PRUEBA -> "Período de prueba: " + dias() + " (hasta el "
                    + fecha(finPrueba.minusDays(1)) + "). Active una licencia para seguir operando después.";
            case ACTIVA -> licencia.perpetua()
                    ? "Licencia activa, sin vencimiento."
                    : "Licencia activa hasta el " + fecha(licencia.vencimiento()) + ".";
            case POR_VENCER -> "La licencia vence " + (diasRestantes == 0 ? "hoy" : "en " + dias()) + " ("
                    + fecha(licencia.vencimiento()) + "). Renuévela para seguir operando.";
            case PRUEBA_VENCIDA -> "Terminó el período de prueba." + SOLO_CONSULTA
                    + " Active una licencia para seguir operando.";
            case VENCIDA -> "La licencia venció el " + fecha(licencia.vencimiento()) + "." + SOLO_CONSULTA
                    + " Renueve la licencia para seguir operando.";
            case EQUIPO_NO_HABILITADO -> "Esta PC (código " + codigoEquipo + ") no está incluida en la licencia."
                    + SOLO_CONSULTA + " Pida a su proveedor una licencia que incluya este código.";
            case INVALIDA -> "La licencia guardada no es válida: " + detalle + SOLO_CONSULTA
                    + " Active una licencia válida.";
            case RELOJ_ATRASADO -> "La fecha de esta PC (" + fecha(fecha) + ") es anterior a la última fecha en "
                    + "que se usó el sistema (" + detalle + "). Corrija la fecha y la hora de la PC." + SOLO_CONSULTA;
        };
    }

    private String dias() {
        return diasRestantes + (diasRestantes == 1 ? " día" : " días");
    }

    private static String fecha(LocalDate fecha) {
        return FORMATO_FECHA.format(fecha);
    }

}
