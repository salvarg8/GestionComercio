package org.gestionComercio.mapper;

import org.gestionComercio.dto.permiso.PermisoDto;
import org.gestionComercio.entity.Permiso;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface PermisoMapper extends BaseMapper<PermisoDto, Permiso> {

    @Override
    PermisoDto toDto(Permiso entity);

    @Override
    Permiso toEntity(PermisoDto dto);

    @Override
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    void updateEntity(
            PermisoDto dto,
            @MappingTarget Permiso entity
    );
}