package org.gestionComercio.viewModel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.Getter;
import org.gestionComercio.dto.permiso.PermisoDto;

@Getter
public class PermisoItem {

    private final PermisoDto permiso;

    private final BooleanProperty seleccionado =
            new SimpleBooleanProperty(false);

    public PermisoItem(PermisoDto permiso) {
        this.permiso = permiso;
    }
}