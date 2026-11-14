package org.gestionComercio.mapper;

import org.gestionComercio.dto.marca.MarcaDto;
import org.gestionComercio.entity.Marca;
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
public interface MarcaMapper extends BaseMapper<MarcaDto, Marca> {

    @Override
    MarcaDto toDto(Marca entity);

    @Override
    @Mapping(target = "estadoRegistro", ignore = true)
    @Mapping(target = "productos", ignore = true)
    Marca toEntity(MarcaDto dto);

    /*
     * Sin IGNORE de nulos a propósito: si se borra la descripción en el
     * formulario, tiene que quedar vacía en la base.
     */
    @Override
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estadoRegistro", ignore = true)
    @Mapping(target = "productos", ignore = true)
    void updateEntity(
            MarcaDto dto,
            @MappingTarget Marca entity
    );
}
