package org.gestionComercio.mapper;

import org.gestionComercio.dto.ProductoDto;
import org.gestionComercio.entity.Producto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ProductoMapper extends BaseMapper<ProductoDto, Producto> {

    @Override
    @Mapping(target = "categoriaId", source = "categoria.id")
    @Mapping(target = "marcaId", source = "marca.id")
    @Mapping(target = "proveedorId", source = "proveedor.id")
    ProductoDto toDto(Producto entity);

    @Override
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "marca", ignore = true)
    @Mapping(target = "proveedor", ignore = true)
    @Mapping(target = "detallesVenta", ignore = true)
    @Mapping(target = "detallesCompra", ignore = true)
    @Mapping(target = "movimientosStock", ignore = true)
    Producto toEntity(ProductoDto dto);

    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "marca", ignore = true)
    @Mapping(target = "proveedor", ignore = true)
    @Mapping(target = "detallesVenta", ignore = true)
    @Mapping(target = "detallesCompra", ignore = true)
    @Mapping(target = "movimientosStock", ignore = true)
    void updateEntity(
            ProductoDto dto,
            @MappingTarget Producto entity
    );

}