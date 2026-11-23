package org.gestionComercio.arca;

import org.gestionComercio.exception.BusinessException;

/**
 * ARCA respondió con un error (SOAP fault, error del servicio o respuesta
 * inválida). El mensaje se puede mostrar al usuario.
 */
public class ArcaException extends BusinessException {

    public ArcaException(String message) {
        super(message);
    }

    public ArcaException(String message, Throwable cause) {
        super(message, cause);
    }

}
