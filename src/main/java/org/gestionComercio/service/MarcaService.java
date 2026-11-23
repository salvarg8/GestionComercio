package org.gestionComercio.service;

import org.gestionComercio.dto.marca.MarcaDto;
import org.gestionComercio.enums.FiltroEstado;

import java.util.List;

/**
 * Las marcas no se eliminan: se dan de baja y se pueden volver a habilitar.
 * Los productos que ya usan una marca dada de baja la conservan.
 */
public interface MarcaService extends CrudService<MarcaDto, Long> {

    /** Busca por nombre (texto vacío = todas) entre las marcas del estado indicado. */
    List<MarcaDto> buscar(String texto, FiltroEstado filtro);

    /** Requiere MARCA_DAR_BAJA. */
    void darDeBaja(Long id);

    /** Requiere MARCA_HABILITAR. */
    void habilitar(Long id);

    /**
     * Equivale a {@link #darDeBaja(Long)}: las marcas nunca se borran de la base.
     */
    @Override
    void delete(Long id);

    /**
     * Marcas activas para elegir en otros formularios (por ejemplo, el de productos).
     * Acepta MARCA_VER, PRODUCTO_CREAR, PRODUCTO_EDITAR o PRODUCTO_ACTUALIZAR_PRECIOS, para que quien carga
     * productos pueda elegir la marca sin tener acceso al ABM de marcas.
     */
    List<MarcaDto> findParaSeleccion();

}
