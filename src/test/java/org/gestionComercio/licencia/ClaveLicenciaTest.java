package org.gestionComercio.licencia;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaveLicenciaTest {

    private final VerificadorLicencia verificador = LicenciasDePrueba.verificador();

    @Test
    @DisplayName("una licencia firmada se verifica y conserva todos sus datos")
    void idaYVuelta() {

        Licencia original = LicenciasDePrueba.licencia(LocalDateTime.of(2026, 11, 23, 9, 30, 15),
                LocalDate.of(2027, 11, 30), EnumSet.of(ModuloLicencia.FACTURACION_ELECTRONICA),
                "aaaa-bbbb-cccc-dddd", LicenciasDePrueba.OTRO_EQUIPO);

        Licencia leida = verificador.verificar(LicenciasDePrueba.firmar(original));

        assertEquals(original, leida);
        assertEquals("Almacén Don José, S.R.L.", leida.cliente());
        assertTrue(leida.incluyeEquipo("AAAABBBBCCCCDDDD"));
        assertTrue(leida.tieneModulo(ModuloLicencia.FACTURACION_ELECTRONICA));
    }

    @Test
    @DisplayName("sin vencimiento es de pago único")
    void perpetua() {

        Licencia leida = verificador.verificar(LicenciasDePrueba.firmar(
                LicenciasDePrueba.licencia(null, LicenciasDePrueba.EQUIPO)));

        assertTrue(leida.perpetua());
        assertNull(leida.vencimiento());
    }

    @Test
    @DisplayName("se acepta el archivo .lic completo, con encabezado y la clave cortada en varias líneas")
    void archivoConComentariosYSaltos() {

        Licencia licencia = LicenciasDePrueba.licencia(null, LicenciasDePrueba.EQUIPO);
        String clave = LicenciasDePrueba.firmar(licencia);
        String cortada = clave.substring(0, 40) + "\r\n  " + clave.substring(40, 90) + "\n" + clave.substring(90);

        assertEquals(licencia, verificador.verificar(ClaveLicencia.archivo(licencia, cortada)));
    }

    @Test
    @DisplayName("modificar los datos de la licencia la invalida")
    void datosModificados() {

        String clave = LicenciasDePrueba.firmar(LicenciasDePrueba.licencia(LocalDate.of(2027, 1, 31),
                LicenciasDePrueba.EQUIPO));
        String[] partes = clave.split("\\.");

        String datos = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace("vencimiento=2027-01-31", "vencimiento=2099-01-31");
        String adulterada = partes[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(datos.getBytes(StandardCharsets.UTF_8)) + "." + partes[2];

        assertThrows(LicenciaInvalidaException.class, () -> verificador.verificar(adulterada));
    }

    @Test
    @DisplayName("cambiar un solo carácter de la clave la invalida")
    void caracterCambiado() {

        String clave = LicenciasDePrueba.firmar(LicenciasDePrueba.licencia(null, LicenciasDePrueba.EQUIPO));
        int posicion = clave.indexOf('.') + 20;
        char cambiado = clave.charAt(posicion) == 'A' ? 'B' : 'A';
        String adulterada = clave.substring(0, posicion) + cambiado + clave.substring(posicion + 1);

        assertThrows(LicenciaInvalidaException.class, () -> verificador.verificar(adulterada));
    }

    @Test
    @DisplayName("una licencia firmada con otra clave privada no se acepta")
    void otroProveedor() {

        String clave = LicenciasDePrueba.firmar(LicenciasDePrueba.licencia(null, LicenciasDePrueba.EQUIPO));

        assertThrows(LicenciaInvalidaException.class,
                () -> LicenciasDePrueba.verificadorDeOtroProveedor().verificar(clave));
    }

    @Test
    @DisplayName("un texto que no es una clave de licencia se rechaza con un mensaje claro")
    void textoCualquiera() {

        LicenciaInvalidaException e = assertThrows(LicenciaInvalidaException.class,
                () -> verificador.verificar("hola"));
        assertEquals("El texto ingresado no es una clave de licencia.", e.getMessage());

        assertThrows(LicenciaInvalidaException.class, () -> verificador.verificar("GC1.%%%.%%%"));
        assertThrows(LicenciaInvalidaException.class, () -> verificador.verificar(null));
    }

    @Test
    @DisplayName("la clave pública de la aplicación es una clave Ed25519 válida")
    void clavePublicaDeLaAplicacion() {
        assertDoesNotThrow(() -> new VerificadorLicencia());
    }

    @Test
    @DisplayName("el código de equipo se compara sin guiones ni mayúsculas")
    void codigoDeEquipo() {

        assertEquals("A1B2C3D4", IdentificadorEquipo.normalizar(" a1b2-c3d4 "));
        assertEquals("A1B2-C3D4-E5F6-0000", IdentificadorEquipo.formatear("a1b2c3d4e5f60000"));
        assertFalse(LicenciasDePrueba.licencia(null, LicenciasDePrueba.EQUIPO).incluyeEquipo(null));
    }

    @Test
    @DisplayName("el código de equipo es estable y tiene el formato XXXX-XXXX-XXXX-XXXX")
    void codigoCalculado() {

        String codigo = IdentificadorEquipoSistema.calcular("5c6d9a4e-1111-2222-3333-444455556666");

        assertEquals(codigo, IdentificadorEquipoSistema.calcular(" 5C6D9A4E-1111-2222-3333-444455556666 "));
        assertTrue(codigo.matches("[0-9A-F]{4}(-[0-9A-F]{4}){3}"), codigo);
    }

}
