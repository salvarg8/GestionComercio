package org.gestionComercio.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NumberUtilsTest {

    @Test
    @DisplayName("parseDecimal() acepta los formatos habituales")
    void parseDecimalFormatos() {
        assertEquals(new BigDecimal("1234.5"), NumberUtils.parseDecimal("1234,5"));
        assertEquals(new BigDecimal("1234.50"), NumberUtils.parseDecimal("1.234,50"));
        assertEquals(new BigDecimal("1234.5"), NumberUtils.parseDecimal("1234.5"));
        assertEquals(new BigDecimal("1234.50"), NumberUtils.parseDecimal("$ 1.234,50"));
        assertEquals(new BigDecimal("0"), NumberUtils.parseDecimal(" 0 "));
        assertEquals(new BigDecimal("-5"), NumberUtils.parseDecimal("-5"));
    }

    @Test
    @DisplayName("parseDecimal() devuelve null si el texto está vacío")
    void parseDecimalVacio() {
        assertNull(NumberUtils.parseDecimal(null));
        assertNull(NumberUtils.parseDecimal("   "));
    }

    @Test
    @DisplayName("parseDecimal() rechaza textos que no son números")
    void parseDecimalInvalido() {
        assertThrows(NumberFormatException.class, () -> NumberUtils.parseDecimal("abc"));
        assertThrows(NumberFormatException.class, () -> NumberUtils.parseDecimal("1,2,3"));
        assertThrows(NumberFormatException.class, () -> NumberUtils.parseDecimal("12a"));
    }

    @Test
    @DisplayName("parseEntero() interpreta enteros y rechaza decimales")
    void parseEntero() {
        assertEquals(5, NumberUtils.parseEntero(" 5 "));
        assertNull(NumberUtils.parseEntero(""));
        assertThrows(NumberFormatException.class, () -> NumberUtils.parseEntero("5,5"));
    }

    @Test
    @DisplayName("formatDecimalEditable() usa coma decimal y dos decimales")
    void formatDecimalEditable() {
        assertEquals("1234,50", NumberUtils.formatDecimalEditable(new BigDecimal("1234.5")));
        assertEquals("", NumberUtils.formatDecimalEditable(null));
    }
}
