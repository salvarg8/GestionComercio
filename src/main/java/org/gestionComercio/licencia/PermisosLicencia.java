package org.gestionComercio.licencia;

import org.gestionComercio.enums.PermisoCodigo;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ajusta los permisos del usuario a la licencia al iniciar sesión:
 * <ul>
 *     <li>si la licencia no permite operar, quedan sólo los de consulta
 *         (ver datos, reportes, crear copias de seguridad y administrar la
 *         licencia), para que el comercio pueda seguir consultando y
 *         exportando su información;</li>
 *     <li>sin un módulo, se quitan sus permisos.</li>
 * </ul>
 * Como todas las pantallas y servicios controlan los permisos de la sesión,
 * esto alcanza para bloquear las operaciones.
 */
public final class PermisosLicencia {

    /** Permisos que no son de visualización pero se mantienen en modo sólo consulta. */
    private static final Set<PermisoCodigo> CONSULTA_ADICIONALES = EnumSet.of(
            PermisoCodigo.BACKUP_CREAR,
            PermisoCodigo.LICENCIA_ADMINISTRAR);

    private PermisosLicencia() {
    }

    public static Set<PermisoCodigo> habilitados(Set<PermisoCodigo> permisos, EstadoLicencia estado) {

        return permisos.stream()
                .filter(p -> estado.permiteOperar() || esDeConsulta(p))
                .filter(p -> moduloHabilitado(p, estado))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermisoCodigo.class)));
    }

    /** Permisos que se conservan en modo sólo consulta. */
    public static boolean esDeConsulta(PermisoCodigo permiso) {
        String nombre = permiso.name();
        return nombre.endsWith("_VER")
                || nombre.contains("_VER_")
                || nombre.startsWith("REPORTE_")
                || CONSULTA_ADICIONALES.contains(permiso);
    }

    private static boolean moduloHabilitado(PermisoCodigo permiso, EstadoLicencia estado) {
        return Arrays.stream(ModuloLicencia.values())
                .filter(m -> m.getPermisos().contains(permiso))
                .allMatch(estado::tieneModulo);
    }

}
