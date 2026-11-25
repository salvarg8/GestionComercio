package org.gestionComercio.controller.rol;

import org.gestionComercio.dto.permiso.PermisoDto;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.viewModel.PermisoItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermisosPorModuloTest {

    private static List<PermisoItem> items(PermisoCodigo... codigos) {
        return Arrays.stream(codigos)
                .map(codigo -> new PermisoItem(PermisoDto.builder()
                        .codigo(codigo)
                        .descripcion(codigo.getDescripcion())
                        .build()))
                .toList();
    }

    private static List<PermisoCodigo> codigos(List<PermisoItem> items) {
        return items.stream().map(item -> item.getPermiso().getCodigo()).toList();
    }

    @Test
    @DisplayName("✓ sin filtro agrupa por módulo en el orden de PermisoCodigo")
    void agrupaPorModuloEnOrden() {
        Map<String, List<PermisoItem>> grupos = PermisosPorModulo.agrupar(
                items(PermisoCodigo.VENTA_VER, PermisoCodigo.ROL_EDITAR, PermisoCodigo.ROL_VER,
                        PermisoCodigo.USUARIO_VER),
                "  ");

        assertEquals(List.of("Usuarios", "Roles", "Ventas"), List.copyOf(grupos.keySet()));
        assertEquals(List.of(PermisoCodigo.ROL_VER, PermisoCodigo.ROL_EDITAR), codigos(grupos.get("Roles")));
    }

    @Test
    @DisplayName("✓ el filtro por descripción deja sólo los permisos que coinciden, sin importar acentos")
    void filtraPorDescripcion() {
        Map<String, List<PermisoItem>> grupos = PermisosPorModulo.agrupar(
                items(PermisoCodigo.CATEGORIA_VER, PermisoCodigo.CATEGORIA_CREAR, PermisoCodigo.MARCA_CREAR),
                "CATEGORIAS");

        assertEquals(List.of("Categorías"), List.copyOf(grupos.keySet()));

        grupos = PermisosPorModulo.agrupar(
                items(PermisoCodigo.CATEGORIA_VER, PermisoCodigo.CATEGORIA_CREAR, PermisoCodigo.MARCA_CREAR),
                "crear");

        assertEquals(List.of(PermisoCodigo.CATEGORIA_CREAR), codigos(grupos.get("Categorías")));
        assertEquals(List.of(PermisoCodigo.MARCA_CREAR), codigos(grupos.get("Marcas")));
    }

    @Test
    @DisplayName("✓ si el filtro coincide con el módulo quedan todos sus permisos")
    void filtroPorModuloDejaTodoElModulo() {
        Map<String, List<PermisoItem>> grupos = PermisosPorModulo.agrupar(
                items(PermisoCodigo.FACTURACION_CONFIGURAR, PermisoCodigo.FACTURACION_REINTENTAR,
                        PermisoCodigo.VENTA_VER),
                "facturación electrónica");

        assertEquals(List.of("Facturación electrónica"), List.copyOf(grupos.keySet()));
        assertEquals(2, grupos.get("Facturación electrónica").size());
    }

    @Test
    @DisplayName("✓ también busca por código y, sin coincidencias, no deja módulos")
    void filtraPorCodigo() {
        List<PermisoItem> permisos = items(PermisoCodigo.CAJA_ABRIR_OTRO_USUARIO, PermisoCodigo.CAJA_VER);

        assertEquals(List.of(PermisoCodigo.CAJA_ABRIR_OTRO_USUARIO),
                codigos(PermisosPorModulo.agrupar(permisos, "abrir otro").get("Caja")));
        assertTrue(PermisosPorModulo.agrupar(permisos, "xyz").isEmpty());
    }
}
