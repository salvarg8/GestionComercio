package org.gestionComercio.mapper;

import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.entity.Empresa;
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
public interface EmpresaMapper extends BaseMapper<EmpresaDto, Empresa> {

    @Override
    EmpresaDto toDto(Empresa entity);

    @Override
    @Mapping(target = "estadoRegistro", ignore = true)
    @Mapping(target = "usuarios", ignore = true)
    Empresa toEntity(EmpresaDto dto);

    /*
     * Sin IGNORE de nulos a propósito: si se borra un campo opcional en el
     * formulario (teléfono, email, logo, etc.), tiene que quedar vacío en la base.
     */
    @Override
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estadoRegistro", ignore = true)
    @Mapping(target = "usuarios", ignore = true)
    void updateEntity(
            EmpresaDto dto,
            @MappingTarget Empresa entity
    );
}
