package org.gestionComercio.controller.licencia;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import org.gestionComercio.licencia.EstadoLicencia;
import org.springframework.stereotype.Component;

/**
 * Avisa a las pantallas abiertas que cambió el estado de la licencia (por
 * ejemplo, el indicador del encabezado al activar una licencia nueva).
 *
 * <p>Quien escuche debe registrarse con un {@code WeakChangeListener}: este
 * componente vive toda la aplicación y las pantallas se recrean en cada sesión.
 */
@Component
public class CambiosLicencia {

    private final ReadOnlyObjectWrapper<EstadoLicencia> estado = new ReadOnlyObjectWrapper<>();

    public ReadOnlyObjectProperty<EstadoLicencia> estadoProperty() {
        return estado.getReadOnlyProperty();
    }

    public void publicar(EstadoLicencia nuevo) {
        estado.set(nuevo);
    }

}
