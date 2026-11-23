package org.gestionComercio.arca;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;

/** Certificado del comercio y su clave privada, con que se firma el pedido de acceso al WSAA. */
public record CredencialArca(PrivateKey clavePrivada, X509Certificate certificado) {

    public Instant vencimiento() {
        return certificado.getNotAfter().toInstant();
    }

    public boolean vencido(Instant ahora) {
        return ahora.isAfter(vencimiento());
    }

    @Override
    public String toString() {
        // Nunca mostrar la clave privada.
        return "CredencialArca[" + CertificadosArca.titular(certificado) + "]";
    }

}
