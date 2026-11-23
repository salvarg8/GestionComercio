package org.gestionComercio.arca;

/**
 * No se obtuvo respuesta de ARCA (sin conexión, tiempo agotado, error HTTP
 * sin cuerpo SOAP). Si la llamada pedía un CAE, no se sabe si ARCA lo
 * autorizó: hay que consultarlo antes de reintentar.
 */
public class ArcaComunicacionException extends ArcaException {

    public ArcaComunicacionException(String message) {
        super(message);
    }

    public ArcaComunicacionException(String message, Throwable cause) {
        super(message, cause);
    }

}
