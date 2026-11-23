package org.gestionComercio.licencia;

/** Consulta si la licencia vigente habilita un módulo (sin requerir permisos). */
public interface ModulosHabilitados {

    boolean habilitado(ModuloLicencia modulo);

}
