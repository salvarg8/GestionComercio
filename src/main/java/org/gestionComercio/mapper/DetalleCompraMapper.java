package org.gestionComercio.mapper;

import org.gestionComercio.dto.compra.DetalleCompraDto;
import org.gestionComercio.entity.DetalleCompra;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/** Sólo convierte de entidad a DTO: los renglones los arma {@code CompraService}. */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface DetalleCompraMapper {

    @Mapping(target = "productoId", source = "producto.id")
    @Mapping(target = "productoCodigo", source = "producto.codigo")
    @Mapping(target = "productoNombre", source = "producto.nombre")
    @Mapping(target = "productoUnidadMedida", source = "producto.unidadMedida")
    DetalleCompraDto toDto(DetalleCompra entity);

    List<DetalleCompraDto> toDtoList(List<DetalleCompra> entities);

}
