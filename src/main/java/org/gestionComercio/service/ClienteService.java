package org.gestionComercio.service;

import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.enums.FiltroEstado;

import java.util.List;

/**
 * Los clientes se dan de baja (baja lógica) y se pueden volver a habilitar.
 * Sólo se pueden eliminar de la base si no tienen ventas asociadas.
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
     * Borra el cliente de la base (esté activo o dado de baja). Requiere
     * CLIENTE_ELIMINAR y falla con BusinessException si tiene ventas asociadas:
     * en ese caso sólo se puede dar de baja.
     */
    void eliminar(Long id);

    /**
     * Equivale a {@link #darDeBaja(Long)}: para borrar de la base hay que usar
     * {@link #eliminar(Long)} explícitamente.
     */
    @Override
    void delete(Long id);

}
