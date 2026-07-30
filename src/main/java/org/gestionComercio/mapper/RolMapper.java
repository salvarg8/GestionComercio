package org.gestionComercio.mapper;

import org.gestionComercio.dto.rol.RolDto;
import org.gestionComercio.entity.Permiso;
import org.gestionComercio.entity.Rol;
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
public interface RolMapper extends BaseMapper<RolDto, Rol> {

    @Override
    @Mapping(target = "permisoIds", source = "permisos")
    RolDto toDto(Rol entity);

    @Override
    @Mapping(target = "permisos", ignore = true)
    Rol toEntity(RolDto dto);

    @Override
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "permisos", ignore = true)
    void updateEntity(
            RolDto dto,
            @MappingTarget Rol entity
    );

    default Long map(Permiso permiso) {
        return permiso.getId();
    }
}