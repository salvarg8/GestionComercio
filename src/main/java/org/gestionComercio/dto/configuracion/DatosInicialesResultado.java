package org.gestionComercio.dto.configuracion;

import org.gestionComercio.enums.Rubro;

import java.util.List;

/**
 * Resultado de cargar los datos iniciales de un rubro.
 *
 * @param rubro      el rubro elegido
 * @param creadas    categorías que se crearon
 * @param existentes categorías que ya existían (con ese nombre) y se dejaron como estaban
 */
public record DatosInicialesResultado(Rubro rubro, List<String> creadas, List<String> existentes) {

    public DatosInicialesResultado {
        creadas = List.copyOf(creadas);
        existentes = List.copyOf(existentes);
    }
}
