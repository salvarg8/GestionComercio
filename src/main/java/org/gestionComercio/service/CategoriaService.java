package org.gestionComercio.service;

import org.gestionComercio.dto.categoria.CategoriaDto;

import java.util.List;

public interface CategoriaService extends CrudService<CategoriaDto, Long> {

    /**
     * Categorías activas para elegir en otros formularios (por ejemplo, el de productos).
     * Acepta CATEGORIA_VER, PRODUCTO_CREAR o PRODUCTO_EDITAR, para que quien carga
     * productos pueda elegir la categoría sin tener acceso al ABM de categorías.
     */
    List<CategoriaDto> findParaSeleccion();

}
