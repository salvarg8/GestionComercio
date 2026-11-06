package org.gestionComercio.mapper;

import org.gestionComercio.dto.usuario.UsuarioDto;
import org.gestionComercio.entity.Usuario;
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
public interface UsuarioMapper extends BaseMapper<UsuarioDto, Usuario> {

    @Override
    @Mapping(target = "rolId", source = "rol.id")
    @Mapping(target = "rolNombre", source = "rol.nombre")
    @Mapping(target = "password", ignore = true)
    UsuarioDto toDto(Usuario entity);

    @Override
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "rol", ignore = true)
    @Mapping(target = "empresa", ignore = true)
    Usuario toEntity(UsuarioDto dto);

    @Override
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "rol", ignore = true)
    @Mapping(target = "empresa", ignore = true)
    void updateEntity(
            UsuarioDto dto,
            @MappingTarget Usuario entity
    );
}
