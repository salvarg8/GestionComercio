package org.gestionComercio.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SituacionStockTest {

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    @DisplayName("Un producto que no maneja stock no tiene situación de stock, tenga el stock que tenga")
    void noManejaStock() {
        assertEquals(SituacionStock.NO_MANEJA_STOCK, SituacionStock.de(false, bd("0"), bd("5")));
        assertEquals(SituacionStock.NO_MANEJA_STOCK, SituacionStock.de(null, bd("10"), bd("5")));
    }

    @Test
    @DisplayName("Stock en cero o negativo es sin stock, aunque el mínimo sea cero")
    void sinStock() {
        assertEquals(SituacionStock.SIN_STOCK, SituacionStock.de(true, bd("0"), bd("5")));
        assertEquals(SituacionStock.SIN_STOCK, SituacionStock.de(true, bd("0.000"), bd("0")));
        assertEquals(SituacionStock.SIN_STOCK, SituacionStock.de(true, bd("-2"), bd("0")));
        assertEquals(SituacionStock.SIN_STOCK, SituacionStock.de(true, null, null));
    }

    @Test
    @DisplayName("Stock positivo en el mínimo o por debajo es stock bajo")
    void stockBajo() {
        assertEquals(SituacionStock.STOCK_BAJO, SituacionStock.de(true, bd("5"), bd("5")));
        assertEquals(SituacionStock.STOCK_BAJO, SituacionStock.de(true, bd("0.250"), bd("1")));
    }

    @Test
    @DisplayName("Stock por encima del mínimo es normal")
    void normal() {
        assertEquals(SituacionStock.NORMAL, SituacionStock.de(true, bd("6"), bd("5")));
        assertEquals(SituacionStock.NORMAL, SituacionStock.de(true, bd("1"), null));
    }

    @Test
    @DisplayName("Requieren reposición sólo los productos sin stock o con stock bajo")
    void requiereReposicion() {
        assertTrue(SituacionStock.SIN_STOCK.requiereReposicion());
        assertTrue(SituacionStock.STOCK_BAJO.requiereReposicion());
        assertFalse(SituacionStock.NORMAL.requiereReposicion());
        assertFalse(SituacionStock.NO_MANEJA_STOCK.requiereReposicion());
    }
}
