package org.gestionComercio.arca;

import java.util.Optional;

/** Almacén de la clave para los tests: no escribe en el registro de Windows. */
public class AlmacenClaveEnMemoria implements AlmacenClaveLocal {

    private volatile String clave;

    @Override
    public Optional<String> leer() {
        return Optional.ofNullable(clave);
    }

    @Override
    public void guardar(String clave) {
        this.clave = clave;
    }

    @Override
    public void borrar() {
        this.clave = null;
    }

}
