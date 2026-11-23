package org.gestionComercio.impresion;

import org.gestionComercio.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Impresora de los comprobantes elegida en esta PC. Cada caja tiene la suya,
 * así que se guarda en las preferencias del usuario de Windows (registro,
 * HKEY_CURRENT_USER) y no en la base compartida. Sin impresora elegida, los
 * comprobantes se abren en el visor de PDF.
 */
@Component
public class ImpresoraLocal {

    private static final String NODO = "/org/gestionComercio/impresion";
    private static final String CLAVE = "impresora-comprobantes";

    /** @return el nombre de la impresora elegida, o vacío si se usa el visor de PDF */
    public Optional<String> leer() {
        String valor = nodo().get(CLAVE, null);
        return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor);
    }

    /** @param impresora nombre de la impresora; null o vacío para volver al visor de PDF */
    public void guardar(String impresora) {

        if (impresora == null || impresora.isBlank()) {
            nodo().remove(CLAVE);
        } else {
            nodo().put(CLAVE, impresora.strip());
        }

        try {
            nodo().flush();
        } catch (BackingStoreException e) {
            throw new BusinessException("No se pudo guardar la impresora en esta PC: " + e.getMessage(), e);
        }
    }

    private static Preferences nodo() {
        return Preferences.userRoot().node(NODO);
    }

}
