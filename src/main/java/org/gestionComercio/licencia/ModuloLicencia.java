package org.gestionComercio.licencia;

import lombok.Getter;
import org.gestionComercio.enums.PermisoCodigo;

import java.util.Set;

/**
 * Módulos que se habilitan por licencia. Sin el módulo, sus permisos se
 * quitan al iniciar sesión y la funcionalidad queda deshabilitada.
 */
@Getter
public enum ModuloLicencia {

    FACTURACION_ELECTRONICA("Facturación electrónica",
            PermisoCodigo.FACTURACION_CONFIGURAR, PermisoCodigo.FACTURACION_REINTENTAR);

    private final String descripcion;

    private final Set<PermisoCodigo> permisos;

    ModuloLicencia(String descripcion, PermisoCodigo... permisos) {
        this.descripcion = descripcion;
        this.permisos = Set.of(permisos);
    }

}
