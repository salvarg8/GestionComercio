package org.gestionComercio.service;

import org.gestionComercio.dto.reporte.Reporte;
import org.gestionComercio.enums.TipoReporte;

import java.time.LocalDate;
import java.util.List;

/**
 * Reportes de gestión. Sólo lectura. Cada reporte exige el permiso indicado
 * en {@link TipoReporte#getPermiso()}. En los totales de ventas y compras no
 * se cuentan las anuladas ni las canceladas.
 */
public interface ReporteService {

    /** Reportes que puede generar el usuario de la sesión, en el orden del menú. */
    List<TipoReporte> disponibles();

    /**
     * Genera un reporte.
     *
     * @param tipo  reporte a generar
     * @param desde primer día del período (null = sin límite); se ignora en los
     *              reportes que no usan fechas
     * @param hasta último día del período, incluido (null = sin límite)
     * @throws org.gestionComercio.exception.AuthorizationException si no tiene el permiso del reporte
     * @throws org.gestionComercio.exception.BusinessException      si falta el tipo o el período es inválido
     */
    Reporte generar(TipoReporte tipo, LocalDate desde, LocalDate hasta);

}
