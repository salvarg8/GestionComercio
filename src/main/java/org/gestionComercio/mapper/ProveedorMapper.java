package org.gestionComercio.mapper;

import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.entity.Proveedor;
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
public interface ProveedorMapper extends BaseMapper<ProveedorDto, Proveedor> {

    @Override
    ProveedorDto toDto(Proveedor entity);

    @Override
    @Mapping(target = "estadoRegistro", ignore = true)
    @Mapping(target = "productos", ignore = true)
    @Mapping(target = "compras", ignore = true)
    Proveedor toEntity(ProveedorDto dto);

    /*
     * Sin IGNORE de nulos a propósito: si se borra un campo opcional en el
     * formulario (CUIT, teléfono, etc.), tiene que quedar vacío en la base.
     */
    @Override
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estadoRegistro", ignore = true)
    @Mapping(target = "productos", ignore = true)
    @Mapping(target = "compras", ignore = true)
    void updateEntity(
            ProveedorDto dto,
            @MappingTarget Proveedor entity
    );
}
