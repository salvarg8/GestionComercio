package org.gestionComercio.mapper;

import org.gestionComercio.dto.configuracion.ConfiguracionDto;
import org.gestionComercio.entity.Configuracion;
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
public interface ConfiguracionMapper {

    ConfiguracionDto toDto(Configuracion entity);

    /*
     * Sin IGNORE de nulos a propósito: si se borra el texto de pie, tiene que
     * quedar vacío en la base. El servicio completa los demás valores antes.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "estadoRegistro", ignore = true)
    void updateEntity(ConfiguracionDto dto, @MappingTarget Configuracion entity);
}
