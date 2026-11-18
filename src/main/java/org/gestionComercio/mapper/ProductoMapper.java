package org.gestionComercio.mapper;

import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.entity.Producto;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * Las relaciones (categoría, marca, proveedor) las resuelve el servicio a partir
 * de los IDs; el estado y el stock nunca se copian desde el DTO.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ProductoMapper extends BaseMapper<ProductoDto, Producto> {

    @Override
    @Mapping(target = "categoriaId", source = "categoria.id")
    @Mapping(target = "categoriaNombre", source = "categoria.nombre")
    @Mapping(target = "marcaId", source = "marca.id")
    @Mapping(target = "marcaNombre", source = "marca.nombre")
    @Mapping(target = "proveedorId", source = "proveedor.id")
    @Mapping(target = "proveedorNombre", source = "proveedor.razonSocial")
    ProductoDto toDto(Producto entity);

    @Override
    @Mapping(target = "estadoRegistro", ignore = true)
    @Mapping(target = "stock", ignore = true)
    @Mapping(target = "categoria", ignore = true)
    @Mapping(target = "marca", ignore = true)
    @Mapping(target = "proveedor", ignore = true)
    @Mapping(target = "detallesVenta", ignore = true)
    @Mapping(target = "detallesCompra", ignore = true)
    @Mapping(target = "movimientosStock", ignore = true)
    Producto toEntity(ProductoDto dto);

    /*
     * Sin IGNORE de nulos a propósito: si se borra la descripción o el código
     * de barras en el formulario, tiene que quedar vacío en la base.
     */
    @Override
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estadoRegistro", ignore = true)
    @Mapping(target = "stock", ignore = true)
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
