package org.gestionComercio.exception;

/**
 * Otra operación modificó el registro entre que se leyó y se intentó guardar
 * (control de concurrencia optimista con {@code @Version}). La operación se
 * revierte por completo; el usuario puede volver a intentarla con los datos
 * actualizados.
 */
public class ConcurrencyException extends BusinessException {

    public ConcurrencyException(String message) {
        super(message);
    }

    public ConcurrencyException(String message, Throwable cause) {
        super(message, cause);
    }

}
