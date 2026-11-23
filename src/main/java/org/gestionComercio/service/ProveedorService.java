package org.gestionComercio.service;

import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.enums.FiltroEstado;

import java.util.List;

/**
 * Los proveedores no se eliminan: se dan de baja y se pueden volver a habilitar.
 * Los productos y compras que ya usan un proveedor dado de baja lo conservan.
 */
public interface ProveedorService extends CrudService<ProveedorDto, Long> {

    /**
     * Busca por razón social o CUIT (texto vacío = todos) entre los proveedores
     * del estado indicado.
     */
    List<ProveedorDto> buscar(String texto, FiltroEstado filtro);

    /** Requiere PROVEEDOR_DAR_BAJA. */
    void darDeBaja(Long id);

    /** Requiere PROVEEDOR_HABILITAR. */
    void habilitar(Long id);

    /**
     * Equivale a {@link #darDeBaja(Long)}: los proveedores nunca se borran de la base.
     */
    @Override
    void delete(Long id);

    /**
     * Proveedores activos para elegir en otros formularios (por ejemplo, el de productos).
     * Acepta PROVEEDOR_VER, PRODUCTO_CREAR, PRODUCTO_EDITAR o PRODUCTO_ACTUALIZAR_PRECIOS, para que quien carga
     * productos pueda elegir el proveedor sin tener acceso al ABM de proveedores.
     */
    List<ProveedorDto> findParaSeleccion();

}
