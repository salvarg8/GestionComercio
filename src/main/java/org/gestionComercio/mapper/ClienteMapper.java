package org.gestionComercio.mapper;

import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.entity.Cliente;
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
public interface ClienteMapper extends BaseMapper<ClienteDto, Cliente> {

    @Override
    ClienteDto toDto(Cliente entity);

    @Override
    @Mapping(target = "estadoRegistro", ignore = true)
    @Mapping(target = "ventas", ignore = true)
    Cliente toEntity(ClienteDto dto);

    /*
     * Sin IGNORE de nulos a propósito: si se borra un campo opcional en el
     * formulario (teléfono, email, etc.), tiene que quedar vacío en la base.
     */
    @Override
    @BeanMapping(
            nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL
    )
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estadoRegistro", ignore = true)
    @Mapping(target = "ventas", ignore = true)
    void updateEntity(
            ClienteDto dto,
            @MappingTarget Cliente entity
    );
}
