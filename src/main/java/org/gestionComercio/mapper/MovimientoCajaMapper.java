package org.gestionComercio.mapper;

import org.gestionComercio.dto.caja.MovimientoCajaDto;
import org.gestionComercio.entity.MovimientoCaja;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Sólo convierte de entidad a DTO: los movimientos de caja son inmutables y
 * los crea {@code CajaService}.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface MovimientoCajaMapper {

    @Mapping(target = "cajaId", source = "caja.id")
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioNombre", source = "usuario.usuario")
    @Mapping(target = "usuarioNombreCompleto",
            expression = "java(entity.getUsuario() == null ? null : "
                    + "entity.getUsuario().getNombre() + \" \" + entity.getUsuario().getApellido())")
    @Mapping(target = "usuarioAutorizacionId", source = "usuarioAutorizacion.id")
    @Mapping(target = "usuarioAutorizacionNombre", source = "usuarioAutorizacion.usuario")
    @Mapping(target = "usuarioAutorizacionNombreCompleto",
            expression = "java(entity.getUsuarioAutorizacion() == null ? null : "
                    + "entity.getUsuarioAutorizacion().getNombre() + \" \" "
                    + "+ entity.getUsuarioAutorizacion().getApellido())")
    MovimientoCajaDto toDto(MovimientoCaja entity);

    List<MovimientoCajaDto> toDtoList(List<MovimientoCaja> entities);

}
