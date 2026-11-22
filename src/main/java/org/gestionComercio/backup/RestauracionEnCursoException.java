package org.gestionComercio.backup;

/**
 * La aplicación no puede arrancar porque otra PC está restaurando la base.
 */
public class RestauracionEnCursoException extends RuntimeException {

    public RestauracionEnCursoException() {
        super("Otra PC está restaurando una copia de seguridad de la base de datos. "
                + "Espere a que termine y vuelva a abrir la aplicación.");
    }
}
