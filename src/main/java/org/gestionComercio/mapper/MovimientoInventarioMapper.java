package org.gestionComercio.mapper;

import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.entity.MovimientoInventario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Sólo convierte de entidad a DTO. A diferencia del resto de los mappers no
 * extiende {@link BaseMapper}: los movimientos son inmutables y se crean con
 * {@link MovimientoInventario#registrar}, así que no hay toEntity ni updateEntity.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface MovimientoInventarioMapper {

    @Mapping(target = "productoId", source = "producto.id")
    @Mapping(target = "productoCodigo", source = "producto.codigo")
    @Mapping(target = "productoNombre", source = "producto.nombre")
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioNombre", source = "usuario.usuario")
    @Mapping(target = "usuarioNombreCompleto",
            expression = "java(entity.getUsuario() == null ? null : "
                    + "entity.getUsuario().getNombre() + \" \" + entity.getUsuario().getApellido())")
    MovimientoInventarioDto toDto(MovimientoInventario entity);

    List<MovimientoInventarioDto> toDtoList(List<MovimientoInventario> entities);

}
