package org.gestionComercio.mapper;

import org.mapstruct.MappingTarget;

import java.util.List;

public interface BaseMapper<D, E> {

    D toDto(E entity);

    E toEntity(D dto);

    List<D> toDtoList(List<E> entities);

    List<E> toEntityList(List<D> dtos);

    void updateEntity(D dto, @MappingTarget E entity);

}