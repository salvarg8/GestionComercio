package org.gestionComercio.mapper;

import org.gestionComercio.dto.categoria.CategoriaDto;
import org.gestionComercio.entity.Categoria;
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
public interface CategoriaMapper extends BaseMapper<CategoriaDto, Categoria> {

    @Override
    CategoriaDto toDto(Categoria entity);

    @Override
    @Mapping(target = "productos", ignore = true)
    Categoria toEntity(CategoriaDto dto);

    /*
     * Sin IGNORE de nulos a propósito: si se borra la descripción en el
     * formulario, tiene que quedar vacía en la base.
     */
    @Override
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productos", ignore = true)
    void updateEntity(
            CategoriaDto dto,
            @MappingTarget Categoria entity
    );
}
