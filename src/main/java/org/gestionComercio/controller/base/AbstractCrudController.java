package org.gestionComercio.controller.base;

public abstract class AbstractCrudController<T> extends AbstractController {

    /**
     * Carga o recarga los datos de la vista.
     */
    protected abstract void cargarDatos();

    /**
     * Prepara la vista para crear un nuevo registro.
     */
    protected abstract void nuevo();

    /**
     * Edita el registro seleccionado.
     */
    protected abstract void editar();

    /**
     * Elimina el registro seleccionado.
     */
    protected abstract void eliminar();

    /**
     * Actualiza la información mostrada.
     */
    protected abstract void actualizar();

}