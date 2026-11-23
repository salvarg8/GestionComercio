package org.gestionComercio.licencia;

import java.util.Locale;

/**
 * Código que identifica a esta PC en la licencia. Lo ve el usuario en la
 * pantalla de licencia y lo envía al proveedor para que la incluya.
 */
public interface IdentificadorEquipo {

    /** @return el código de esta PC, con el formato {@code XXXX-XXXX-XXXX-XXXX} */
    String obtener();

    /** Forma canónica de un código para compararlo: sin guiones ni espacios, en mayúsculas. */
    static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    /** Agrupa un código normalizado de a 4 caracteres: {@code A1B2C3D4} → {@code A1B2-C3D4}. */
    static String formatear(String codigo) {

        String normalizado = normalizar(codigo);
        StringBuilder texto = new StringBuilder();

        for (int i = 0; i < normalizado.length(); i += 4) {
            if (i > 0) {
                texto.append('-');
            }
            texto.append(normalizado, i, Math.min(i + 4, normalizado.length()));
        }

        return texto.toString();
    }

}
