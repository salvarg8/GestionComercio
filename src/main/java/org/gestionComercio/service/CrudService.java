package org.gestionComercio.service;

import java.util.List;
import java.util.Optional;

public interface CrudService<D, ID> {

    D save(D dto);

    D update(D dto);

    void delete(ID id);

    Optional<D> findById(ID id);

    List<D> findAll();

    List<D> search(String texto);

    long count();

}