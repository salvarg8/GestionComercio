package org.gestionComercio.controller.base;

public abstract class AbstractFormController<T>
        extends AbstractController {

    protected T entity;

    public abstract void load(T entity);

    protected abstract void clear();

    protected abstract void validate();

    protected abstract void save();

}