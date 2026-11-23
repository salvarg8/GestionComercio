package org.gestionComercio.licencia;

/** Para tests de integración ajenos a la licencia: todos los módulos habilitados. */
public class ModulosTodosHabilitados implements ModulosHabilitados {

    @Override
    public boolean habilitado(ModuloLicencia modulo) {
        return true;
    }

}
