package org.gestionComercio.arca;

import java.time.Duration;
import java.time.Instant;

/** Ticket de acceso del WSAA: token y sign que acompañan cada llamada al WSFEv1. */
public record TicketAcceso(String token, String sign, Instant generado, Instant vence) {

    /** Margen antes del vencimiento a partir del cual se pide un ticket nuevo. */
    public static final Duration MARGEN_RENOVACION = Duration.ofMinutes(10);

    /** @return true si se puede seguir usando (vence después de ahora + el margen). */
    public boolean vigente(Instant ahora) {
        return vence.isAfter(ahora.plus(MARGEN_RENOVACION));
    }

    @Override
    public String toString() {
        return "TicketAcceso[vence=" + vence + "]";
    }

}
