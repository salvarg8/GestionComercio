package org.gestionComercio.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CuitUtilsTest {

    @Test
    @DisplayName("normalizar() quita guiones, puntos y espacios")
    void normalizar() {
        assertEquals("30712345671", CuitUtils.normalizar(" 30-71234567-1 "));
        assertEquals("20123456786", CuitUtils.normalizar("20.12345678.6"));
        assertNull(CuitUtils.normalizar("  - "));
        assertNull(CuitUtils.normalizar(null));
    }

    @Test
    @DisplayName("normalizar() no quita letras, para que no pase la validación")
    void normalizarConservaLetras() {
        assertEquals("30A12345671", CuitUtils.normalizar("30-A1234567-1"));
    }

    @Test
    @DisplayName("esValido() acepta CUIT con dígito verificador correcto, con o sin guiones")
    void validos() {
        assertTrue(CuitUtils.esValido("30-71234567-1"));
        assertTrue(CuitUtils.esValido("20123456786"));
    }

    @Test
    @DisplayName("esValido() rechaza dígito verificador incorrecto, largo erróneo o letras")
    void invalidos() {
        assertFalse(CuitUtils.esValido("30-71234567-2"));
        assertFalse(CuitUtils.esValido("2012345678"));
        assertFalse(CuitUtils.esValido("201234567861"));
        assertFalse(CuitUtils.esValido("20-1234567X-6"));
        assertFalse(CuitUtils.esValido(null));
    }

    @Test
    @DisplayName("formatear() agrega los guiones")
    void formatear() {
        assertEquals("30-71234567-1", CuitUtils.formatear("30712345671"));
        assertEquals("", CuitUtils.formatear(null));
    }
}
