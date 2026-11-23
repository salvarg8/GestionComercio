package org.gestionComercio.service;

import org.gestionComercio.dto.configuracion.ConfiguracionDto;
import org.gestionComercio.dto.configuracion.DatosInicialesResultado;
import org.gestionComercio.enums.Rubro;

/**
 * Parámetros generales del sistema. Política: existe un único registro, que
 * crea la migración; no se dan altas ni bajas, sólo se consulta y se edita.
 * Los cambios rigen desde la próxima operación, sin reiniciar la aplicación.
 */
public interface ConfiguracionService {

    /**
     * Los parámetros vigentes, para aplicarlos (punto de venta, productos,
     * comprobantes). No requiere permisos: cualquier usuario con sesión los usa.
     */
    ConfiguracionDto vigente();

    /** Los parámetros, para la pantalla de configuración. Requiere CONFIGURACION_VER. */
    ConfiguracionDto obtener();

    /**
     * Actualiza los parámetros (el ID del DTO se ignora). Los valores null
     * toman su valor por defecto. Requiere CONFIGURACION_EDITAR.
     *
     * @throws org.gestionComercio.exception.ValidationException si algún valor no es válido
     */
    ConfiguracionDto actualizar(ConfiguracionDto dto);

    /**
     * Crea las categorías típicas del rubro que todavía no existen (por
     * nombre, sin distinguir mayúsculas) y propone su unidad de medida para
     * los productos nuevos. Se puede repetir: no duplica nada.
     * Requiere CONFIGURACION_EDITAR.
     */
    DatosInicialesResultado cargarDatosIniciales(Rubro rubro);

}
