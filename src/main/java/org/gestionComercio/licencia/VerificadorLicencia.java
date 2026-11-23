package org.gestionComercio.licencia;

import org.springframework.stereotype.Component;

import java.security.PublicKey;

/**
 * Verifica las claves de licencia con la clave pública del proveedor.
 *
 * <p>La clave pública está fija en el código a propósito: si se pudiera
 * cambiar por configuración, cualquiera podría firmar sus propias licencias.
 * La privada correspondiente no está en el repositorio (ver scripts/licencias).
 */
@Component
public class VerificadorLicencia {

    /** Clave pública Ed25519 (X.509, Base64) con que se verifican las licencias. */
    static final String CLAVE_PUBLICA = "MCowBQYDK2VwAyEAvcqDlz3/Z8HxtTPP7bRnF7H/ZVg0gi8Achujlw5JeaY=";

    private final PublicKey clavePublica;

    public VerificadorLicencia() {
        this(ClaveLicencia.leerClavePublica(CLAVE_PUBLICA));
    }

    /** Para tests, con una clave de prueba. */
    public VerificadorLicencia(PublicKey clavePublica) {
        this.clavePublica = clavePublica;
    }

    /**
     * @throws LicenciaInvalidaException si la clave está mal formada, fue modificada o no la firmó el proveedor
     */
    public Licencia verificar(String clave) {
        return ClaveLicencia.verificar(clave, clavePublica);
    }

}
