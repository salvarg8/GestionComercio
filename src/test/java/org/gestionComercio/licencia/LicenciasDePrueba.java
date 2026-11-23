package org.gestionComercio.licencia;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Par de claves de prueba (no el del proveedor) y licencias firmadas con él. */
public final class LicenciasDePrueba {

    public static final String EQUIPO = "AAAA-BBBB-CCCC-DDDD";

    public static final String OTRO_EQUIPO = "1111-2222-3333-4444";

    private static final KeyPair PAR = generar();

    private LicenciasDePrueba() {
    }

    public static VerificadorLicencia verificador() {
        return new VerificadorLicencia(PAR.getPublic());
    }

    /** Verificador con otro par de claves: rechaza todo lo firmado aquí. */
    public static VerificadorLicencia verificadorDeOtroProveedor() {
        return new VerificadorLicencia(generar().getPublic());
    }

    public static Licencia licencia(LocalDate vencimiento, String... equipos) {
        return licencia(LocalDateTime.of(2026, 1, 1, 10, 0), vencimiento, EnumSet.of(
                ModuloLicencia.FACTURACION_ELECTRONICA), equipos);
    }

    public static Licencia licencia(LocalDateTime emitida, LocalDate vencimiento, Set<ModuloLicencia> modulos,
                                    String... equipos) {
        return new Licencia(UUID.randomUUID().toString(), "Almacén Don José, S.R.L.", "30712345679", emitida,
                vencimiento, modulos, List.of(equipos));
    }

    public static String firmar(Licencia licencia) {
        return ClaveLicencia.firmar(licencia, PAR.getPrivate());
    }

    private static KeyPair generar() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

}
