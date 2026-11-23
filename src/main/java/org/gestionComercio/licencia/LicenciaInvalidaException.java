package org.gestionComercio.licencia;

import org.gestionComercio.exception.BusinessException;

/** La clave de licencia está mal formada, fue modificada o no la firmó el proveedor. */
public class LicenciaInvalidaException extends BusinessException {

    public LicenciaInvalidaException(String message) {
        super(message);
    }

    public LicenciaInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }

}
