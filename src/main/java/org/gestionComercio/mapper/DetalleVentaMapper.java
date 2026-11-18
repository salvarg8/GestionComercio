package org.gestionComercio.mapper;

import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.entity.DetalleVenta;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Sólo convierte de entidad a DTO: los renglones los arma {@code VentaService}
 * con el precio del producto, nunca a partir de un DTO.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface DetalleVentaMapper {

    @Mapping(target = "productoId", source = "producto.id")
    @Mapping(target = "productoCodigo", source = "producto.codigo")
    @Mapping(target = "productoNombre", source = "producto.nombre")
    @Mapping(target = "productoUnidadMedida", source = "producto.unidadMedida")
    DetalleVentaDto toDto(DetalleVenta entity);

    List<DetalleVentaDto> toDtoList(List<DetalleVenta> entities);

}
