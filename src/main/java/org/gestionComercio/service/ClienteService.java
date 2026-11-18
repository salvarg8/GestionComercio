package org.gestionComercio.service;

import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.enums.FiltroEstado;

import java.util.List;

/**
 * Los clientes no se eliminan: se dan de baja (baja lógica) y se pueden volver
 * a habilitar. Las ventas que ya tienen los conservan.
 */
public interface ClienteService extends CrudService<ClienteDto, Long> {

    /**
     * Busca por nombre, apellido o número de documento (texto vacío = todos)
     * entre los clientes del estado indicado.
     */
    List<ClienteDto> buscar(String texto, FiltroEstado filtro);

    /** Requiere CLIENTE_DAR_BAJA. */
    void darDeBaja(Long id);

    /** Requiere CLIENTE_HABILITAR. */
    void habilitar(Long id);

    /**
     * Equivale a {@link #darDeBaja(Long)}: los clientes nunca se borran de la base.
     */
    @Override
    void delete(Long id);

}
