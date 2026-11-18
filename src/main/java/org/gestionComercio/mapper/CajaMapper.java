package org.gestionComercio.mapper;

import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.entity.Caja;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Sólo convierte de entidad a DTO: las cajas se abren y cierran desde
 * {@code CajaService}. Los totales y el saldo esperado de una caja abierta se
 * calculan a partir de los movimientos y los completa el servicio.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface CajaMapper {

    @Mapping(target = "totalIngresos", ignore = true)
    @Mapping(target = "totalEgresos", ignore = true)
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioNombre", source = "usuario.usuario")
    @Mapping(target = "usuarioNombreCompleto",
            expression = "java(entity.getUsuario() == null ? null : "
                    + "entity.getUsuario().getNombre() + \" \" + entity.getUsuario().getApellido())")
    CajaDto toDto(Caja entity);

}
