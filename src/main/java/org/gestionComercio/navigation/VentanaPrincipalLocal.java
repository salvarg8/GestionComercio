package org.gestionComercio.navigation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Estado de la ventana principal en esta PC. Cada equipo tiene sus pantallas,
 * así que se guarda en las preferencias del usuario de Windows (registro,
 * HKEY_CURRENT_USER), como la impresora, y no en la base compartida.
 */
@Slf4j
@Component
public class VentanaPrincipalLocal {

    private static final String NODO = "/org/gestionComercio/ventana";
    private static final String MAXIMIZADA = "maximizada";
    private static final String X = "x";
    private static final String Y = "y";
    private static final String ANCHO = "ancho";
    private static final String ALTO = "alto";

    /** @return el estado guardado, o vacío si nunca se guardó en esta PC */
    public Optional<EstadoVentana> leer() {

        Preferences nodo = nodo();
        if (nodo.get(ANCHO, null) == null) {
            return Optional.empty();
        }

        return Optional.of(new EstadoVentana(
                nodo.getBoolean(MAXIMIZADA, true),
                nodo.getDouble(X, Double.NaN),
                nodo.getDouble(Y, Double.NaN),
                nodo.getDouble(ANCHO, Double.NaN),
                nodo.getDouble(ALTO, Double.NaN)));
    }

    /** No poder recordar la ventana no impide seguir trabajando: el error sólo se registra. */
    public void guardar(EstadoVentana estado) {

        Preferences nodo = nodo();
        nodo.putBoolean(MAXIMIZADA, estado.maximizada());
        nodo.putDouble(X, estado.x());
        nodo.putDouble(Y, estado.y());
        nodo.putDouble(ANCHO, estado.ancho());
        nodo.putDouble(ALTO, estado.alto());

        try {
            nodo.flush();
        } catch (BackingStoreException e) {
            log.warn("No se pudo guardar el estado de la ventana en esta PC", e);
        }
    }

    private static Preferences nodo() {
        return Preferences.userRoot().node(NODO);
    }
}
