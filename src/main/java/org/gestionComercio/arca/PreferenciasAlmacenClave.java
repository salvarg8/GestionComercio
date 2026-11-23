package org.gestionComercio.arca;

import org.gestionComercio.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Guarda la clave en las preferencias del usuario de Windows (registro,
 * HKEY_CURRENT_USER), como la carpeta de las copias de seguridad. Sólo la
 * lee ese usuario de Windows en esta PC; no queda cifrada.
 */
@Component
public class PreferenciasAlmacenClave implements AlmacenClaveLocal {

    private static final String NODO = "/org/gestionComercio/arca";
    private static final String CLAVE = "clave-certificado";

    @Override
    public Optional<String> leer() {
        String valor = nodo().get(CLAVE, null);
        return valor == null || valor.isBlank() ? Optional.empty() : Optional.of(valor);
    }

    @Override
    public void guardar(String clave) {
        nodo().put(CLAVE, clave);
        sincronizar();
    }

    @Override
    public void borrar() {
        nodo().remove(CLAVE);
        sincronizar();
    }

    private static Preferences nodo() {
        return Preferences.userRoot().node(NODO);
    }

    private static void sincronizar() {
        try {
            nodo().flush();
        } catch (BackingStoreException e) {
            throw new BusinessException("No se pudo guardar la clave en esta PC: " + e.getMessage(), e);
        }
    }

}
