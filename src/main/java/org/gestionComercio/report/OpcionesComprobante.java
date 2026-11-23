package org.gestionComercio.report;

import org.gestionComercio.enums.FormatoTicket;

import java.util.Arrays;
import java.util.List;

/**
 * Formato de los comprobantes, según la configuración del sistema.
 *
 * @param formatoTicket formato de los tickets (las facturas y los remitos van siempre en A4)
 * @param imprimirLogo  si se imprime el logo de la empresa
 * @param pie           texto al pie (horario, redes, políticas de cambio); null si no lleva
 */
public record OpcionesComprobante(FormatoTicket formatoTicket, boolean imprimirLogo, String pie) {

    public OpcionesComprobante {
        formatoTicket = formatoTicket == null ? FormatoTicket.porDefecto() : formatoTicket;
        pie = pie == null || pie.isBlank() ? null : pie.strip();
    }

    /** Ticket de 80 mm, con logo y sin texto de pie. */
    public static OpcionesComprobante porDefecto() {
        return new OpcionesComprobante(FormatoTicket.porDefecto(), true, null);
    }

    /** Los renglones del texto de pie, tal como se escribieron; vacía si no lleva. */
    public List<String> lineasPie() {
        return pie == null ? List.of() : Arrays.stream(pie.split("\\R")).map(String::strip).toList();
    }
}
