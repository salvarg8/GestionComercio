package org.gestionComercio.controller.rol;

import org.gestionComercio.util.StringUtils;
import org.gestionComercio.viewModel.PermisoItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agrupa los permisos del formulario de roles por módulo y los filtra según
 * lo escrito en el buscador.
 */
final class PermisosPorModulo {

    private PermisosPorModulo() {
    }

    /**
     * Permisos agrupados por módulo, en el orden en que se declaran en
     * PermisoCodigo. Un filtro vacío deja todos. Si el filtro coincide con el
     * nombre del módulo quedan todos sus permisos; si no, sólo los que
     * coinciden por descripción o código. No distingue mayúsculas ni acentos.
     */
    static Map<String, List<PermisoItem>> agrupar(List<PermisoItem> permisos, String filtro) {

        String buscado = normalizar(filtro);

        Map<String, List<PermisoItem>> porModulo = new LinkedHashMap<>();

        permisos.stream()
                .sorted(Comparator.comparing(item -> item.getPermiso().getCodigo()))
                .filter(item -> buscado.isEmpty() || coincide(item, buscado))
                .forEach(item -> porModulo
                        .computeIfAbsent(modulo(item), m -> new ArrayList<>())
                        .add(item));

        return porModulo;
    }

    static String modulo(PermisoItem item) {
        return item.getPermiso().getCodigo().getModulo();
    }

    private static boolean coincide(PermisoItem item, String buscado) {
        return normalizar(modulo(item)).contains(buscado)
                || normalizar(item.getPermiso().getDescripcion()).contains(buscado)
                || normalizar(item.getPermiso().getCodigo().name().replace('_', ' ')).contains(buscado);
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : StringUtils.removeAccents(texto).trim().toLowerCase();
    }
}
