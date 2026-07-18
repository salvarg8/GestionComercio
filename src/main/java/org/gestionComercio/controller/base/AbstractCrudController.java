package org.gestionComercio.controller.base;

public abstract class AbstractCrudController<T>
        extends AbstractController {

    protected abstract void nuevo();

    protected abstract void editar();

    protected abstract void eliminar();

    protected abstract void actualizar();

}