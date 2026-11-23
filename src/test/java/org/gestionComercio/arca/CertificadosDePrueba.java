package org.gestionComercio.arca;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

import java.io.StringReader;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/** Autoridad certificante de prueba que firma los pedidos de certificado como lo haría ARCA. */
public final class CertificadosDePrueba {

    private static final X500Name EMISOR = new X500Name("C=AR, O=AFIP, CN=Computadores Test");
    private static final AtomicLong SERIE = new AtomicLong(System.currentTimeMillis());
    private static final KeyPair CA = generarClaves();

    private CertificadosDePrueba() {
    }

    /** Firma el CSR (PEM) y devuelve el certificado. */
    public static X509Certificate firmar(String csrPem, Duration validez) {

        try (PEMParser parser = new PEMParser(new StringReader(csrPem))) {

            PKCS10CertificationRequest csr = (PKCS10CertificationRequest) parser.readObject();
            Instant ahora = Instant.now();

            X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
                    EMISOR, BigInteger.valueOf(SERIE.incrementAndGet()),
                    Date.from(ahora.minus(Duration.ofHours(1))), Date.from(ahora.plus(validez)),
                    csr.getSubject(), csr.getSubjectPublicKeyInfo());

            return new JcaX509CertificateConverter()
                    .getCertificate(builder.build(CertificadosArca.firmante(CA.getPrivate())));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Credencial completa (clave + certificado firmado) para el CUIT indicado. */
    public static CredencialArca credencial(String cuit) {
        CertificadosArca.PedidoCertificado pedido = CertificadosArca.generarPedido(cuit, "Comercio de prueba", "pc1");
        return new CredencialArca(pedido.claves().getPrivate(), firmar(pedido.csrPem(), Duration.ofDays(730)));
    }

    private static KeyPair generarClaves() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            return generador.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
