package org.gestionComercio.mapper;

import java.util.List;

public interface BaseMapper<D, E> {

    D toDTO(E entity);

    E toEntity(D dto);

    List<D> toDTOList(List<E> entities);

    List<E> toEntityList(List<D> dtos);

}