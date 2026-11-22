package org.gestionComercio.backup;

import java.util.Arrays;
import java.util.List;

/**
 * Resultado de ejecutar pg_dump o pg_restore.
 *
 * @param codigo código de salida del proceso (0 = sin errores)
 * @param salida salida estándar y de errores, juntas
 */
public record ResultadoComando(int codigo, String salida) {

    private static final int RENGLONES_RESUMEN = 5;

    public boolean exitoso() {
        return codigo == 0;
    }

    /** Últimos renglones de la salida, para mostrar en un mensaje de error. */
    public String resumen() {

        if (salida == null || salida.isBlank()) {
            return "código de salida " + codigo;
        }

        List<String> renglones = Arrays.stream(salida.split("\\R"))
                .filter(r -> !r.isBlank())
                .toList();

        return String.join("\n",
                renglones.subList(Math.max(0, renglones.size() - RENGLONES_RESUMEN), renglones.size()));
    }
}
