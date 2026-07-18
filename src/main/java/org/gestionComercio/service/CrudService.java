package org.gestionComercio.service;

import java.util.List;
import java.util.Optional;

public interface CrudService<T, ID> {

    T save(T entity);

    T update(T entity);

    void delete(ID id);

    Optional<T> findById(ID id);

    List<T> findAll();

}