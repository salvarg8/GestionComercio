package org.gestionComercio.navigation;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Pestañas de la vista Configuración ({@link AppView#CONFIGURACION}). Cada
 * una muestra una o más vistas existentes, que se abren desde ahí y no desde
 * el menú: el {@link Navigator} lleva a la pestaña cuando se navega a una de
 * ellas (por ejemplo, el aviso de licencia o "Cancelar" en el formulario de
 * usuarios).
 *
 * <p>Una pestaña se muestra si el usuario puede acceder a alguna de sus vistas.
 */
@Getter
public enum PestanaConfiguracion {

    EMPRESA("Empresa", AppView.EMPRESA_CONFIG),

    SISTEMA("Sistema", AppView.CONFIGURACION_SISTEMA),

    FACTURACION("Facturación electrónica", AppView.FACTURACION_CONFIG),

    USUARIOS("Usuarios y roles", AppView.USUARIO_LIST, AppView.ROL_LIST),

    BACKUP("Copias de seguridad", AppView.BACKUP),

    LICENCIA("Licencia", AppView.LICENCIA);

    private final String titulo;

    /** Vistas de la pestaña; si hay más de una, cada una va en una sub-pestaña con su título. */
    private final List<AppView> vistas;

    PestanaConfiguracion(String titulo, AppView... vistas) {
        this.titulo = titulo;
        this.vistas = List.of(vistas);
    }

    /** Pestaña que contiene la vista, si es una de las de Configuración. */
    public static Optional<PestanaConfiguracion> de(AppView vista) {
        return Arrays.stream(values())
                .filter(pestana -> pestana.vistas.contains(vista))
                .findFirst();
    }
}
