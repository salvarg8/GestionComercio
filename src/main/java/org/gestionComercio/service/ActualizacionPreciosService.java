package org.gestionComercio.service;

import org.gestionComercio.dto.producto.ActualizacionPrecioDto;
import org.gestionComercio.dto.producto.FiltroActualizacionPrecios;
import org.gestionComercio.dto.producto.PrecioActualizadoDto;

import java.util.List;

/**
 * Actualización masiva de precios por porcentaje, para los productos activos
 * de una categoría, marca o proveedor (o de todos). Primero se genera la
 * vista previa y después se aplica exactamente lo previsto. Cada
 * actualización queda registrada con su usuario, fecha y los precios
 * anteriores y nuevos de cada producto.
 *
 * <p>Todas las operaciones requieren PRODUCTO_ACTUALIZAR_PRECIOS.
 */
public interface ActualizacionPreciosService {

    /**
     * Calcula los precios nuevos sin guardar nada.
     *
     * @throws org.gestionComercio.exception.ValidationException si el filtro no es válido
     */
    List<PrecioActualizadoDto> previsualizar(FiltroActualizacionPrecios filtro);

    /**
     * Aplica la actualización y la registra. Vuelve a calcular los precios y
     * los compara con la vista previa: si desde entonces cambió algún precio
     * o la lista de productos, no aplica nada.
     *
     * @param vistaPrevia lo que devolvió {@link #previsualizar} para el mismo filtro
     * @return el registro de la actualización
     * @throws org.gestionComercio.exception.BusinessException si no hay productos o la vista previa quedó vieja
     */
    ActualizacionPrecioDto aplicar(FiltroActualizacionPrecios filtro, List<PrecioActualizadoDto> vistaPrevia);

    /** Las últimas actualizaciones, de la más reciente a la más antigua. */
    List<ActualizacionPrecioDto> historial();

    /** Los precios anteriores y nuevos de cada producto de una actualización. */
    List<PrecioActualizadoDto> detalle(Long actualizacionId);

}
