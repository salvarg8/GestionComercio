package org.gestionComercio.arca;

import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clave privada, pedido de certificado (CSR) y almacén PKCS#12 del
 * certificado con que el comercio se identifica ante ARCA.
 *
 * <p>ARCA espera en el CSR: C=AR, O=razón social, CN=alias (nombre del
 * "computador fiscal") y serialNumber=CUIT seguido de los 11 dígitos.
 */
public final class CertificadosArca {

    private static final String ALIAS = "arca";
    private static final String ALGORITMO_FIRMA = "SHA256withRSA";
    private static final int BITS_CLAVE = 2048;

    private static final Pattern CUIT_EN_TITULAR = Pattern.compile("CUIT\\s*(\\d{11})");

    private CertificadosArca() {
    }

    /** Clave privada nueva y el CSR en PEM para subir a ARCA. */
    public record PedidoCertificado(KeyPair claves, String csrPem) {
    }

    public static PedidoCertificado generarPedido(String cuit, String razonSocial, String aliasComputador) {

        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(BITS_CLAVE);
            KeyPair claves = generador.generateKeyPair();

            X500Name titular = new X500NameBuilder(BCStyle.INSTANCE)
                    .addRDN(BCStyle.C, "AR")
                    .addRDN(BCStyle.O, razonSocial)
                    .addRDN(BCStyle.CN, aliasComputador)
                    .addRDN(BCStyle.SERIALNUMBER, "CUIT " + cuit)
                    .build();

            PKCS10CertificationRequest csr = new JcaPKCS10CertificationRequestBuilder(titular, claves.getPublic())
                    .build(firmante(claves.getPrivate()));

            return new PedidoCertificado(claves, pem(csr));
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("No se pudo generar el pedido de certificado.", e);
        }
    }

    /**
     * Certificado autofirmado de un día. Un PKCS#12 no guarda una clave
     * privada sin certificado: éste acompaña a la clave del pedido hasta que
     * se importa el que firma ARCA. Nunca se envía a ARCA.
     */
    public static X509Certificate autofirmado(KeyPair claves, String titular) {

        try {
            Instant ahora = Instant.now();
            X500Name nombre = new X500Name(titular);

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    nombre, BigInteger.valueOf(ahora.toEpochMilli()),
                    Date.from(ahora.minus(Duration.ofMinutes(5))), Date.from(ahora.plus(Duration.ofDays(1))),
                    nombre, claves.getPublic());

            return new JcaX509CertificateConverter().getCertificate(builder.build(firmante(claves.getPrivate())));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo generar el certificado provisorio.", e);
        }
    }

    /** Almacén PKCS#12 cifrado con la clave privada y su certificado. */
    public static byte[] crearPkcs12(PrivateKey clave, X509Certificate certificado, char[] contrasenia) {

        try {
            KeyStore almacen = KeyStore.getInstance("PKCS12");
            almacen.load(null, null);
            almacen.setKeyEntry(ALIAS, clave, contrasenia, new Certificate[]{certificado});

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            almacen.store(salida, contrasenia);
            return salida.toByteArray();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("No se pudo guardar el certificado.", e);
        }
    }

    /**
     * Lee la clave privada y el certificado de un PKCS#12 (el primero que
     * tenga clave privada).
     *
     * @throws ArcaException si la contraseña no corresponde o el archivo no es un PKCS#12
     */
    public static CredencialArca leerPkcs12(byte[] pkcs12, char[] contrasenia) {

        try {
            KeyStore almacen = KeyStore.getInstance("PKCS12");
            almacen.load(new ByteArrayInputStream(pkcs12), contrasenia);

            for (String alias : Collections.list(almacen.aliases())) {

                if (!almacen.isKeyEntry(alias)) {
                    continue;
                }

                Key clave = almacen.getKey(alias, contrasenia);

                if (clave instanceof PrivateKey privada && almacen.getCertificate(alias) instanceof X509Certificate c) {
                    return new CredencialArca(privada, c);
                }
            }

            throw new ArcaException("El archivo no contiene una clave privada con su certificado.");
        } catch (IOException e) {
            throw new ArcaException("No se pudo abrir el certificado: la contraseña no corresponde "
                    + "o el archivo no es un PKCS#12 (.p12 / .pfx).", e);
        } catch (GeneralSecurityException e) {
            throw new ArcaException("No se pudo leer el certificado: " + e.getMessage(), e);
        }
    }

    /**
     * Lee un certificado X.509 en PEM o DER (el .crt que descarga ARCA).
     *
     * @throws ArcaException si el archivo no es un certificado
     */
    public static X509Certificate leerCertificado(byte[] contenido) {

        try {
            Certificate certificado = CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(contenido));
            return (X509Certificate) certificado;
        } catch (CertificateException | ClassCastException e) {
            throw new ArcaException("El archivo no es un certificado válido (se espera el .crt descargado de ARCA).",
                    e);
        }
    }

    /** @return true si el certificado corresponde a esa clave pública. */
    public static boolean corresponde(X509Certificate certificado, PublicKey clavePublica) {
        return certificado.getPublicKey().equals(clavePublica);
    }

    /** CUIT del titular del certificado (serialNumber=CUIT nnnnnnnnnnn), si lo tiene. */
    public static Optional<String> cuitDelTitular(X509Certificate certificado) {

        for (RDN rdn : nombre(certificado).getRDNs(BCStyle.SERIALNUMBER)) {

            Matcher m = CUIT_EN_TITULAR.matcher(IETFUtils.valueToString(rdn.getFirst().getValue()));

            if (m.find()) {
                return Optional.of(m.group(1));
            }
        }

        return Optional.empty();
    }

    /** Titular del certificado en forma legible ("C=AR,O=...,CN=...,SERIALNUMBER=CUIT ..."). */
    public static String titular(X509Certificate certificado) {
        return BCStyle.INSTANCE.toString(nombre(certificado));
    }

    private static X500Name nombre(X509Certificate certificado) {
        return X500Name.getInstance(certificado.getSubjectX500Principal().getEncoded());
    }

    static ContentSigner firmante(PrivateKey clave) {
        try {
            return new JcaContentSignerBuilder(ALGORITMO_FIRMA).build(clave);
        } catch (OperatorCreationException e) {
            throw new IllegalStateException("No se pudo preparar la firma " + ALGORITMO_FIRMA + ".", e);
        }
    }

    private static String pem(Object objeto) throws IOException {
        StringWriter texto = new StringWriter();
        try (JcaPEMWriter writer = new JcaPEMWriter(texto)) {
            writer.writeObject(objeto);
        }
        return texto.toString();
    }

}
