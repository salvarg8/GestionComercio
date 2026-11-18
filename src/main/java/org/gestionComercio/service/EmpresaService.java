package org.gestionComercio.service;

import org.gestionComercio.dto.empresa.EmpresaDto;

/**
 * Datos del comercio. Política: existe un único registro de empresa, que se
 * crea al iniciar la aplicación; no se dan altas ni bajas, sólo se consulta
 * y se edita.
 */
public interface EmpresaService {

    /**
     * Devuelve los datos de la empresa. Requiere EMPRESA_VER.
     */
    EmpresaDto obtener();

    /**
     * Actualiza los datos de la única empresa (el ID del DTO se ignora).
     * Requiere EMPRESA_EDITAR.
     */
    EmpresaDto actualizar(EmpresaDto dto);

}
