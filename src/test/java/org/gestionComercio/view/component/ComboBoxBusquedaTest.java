package org.gestionComercio.view.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComboBoxBusquedaTest {

    private static final String PRODUCTO = "YER001 Yerba Mate Suave 1kg 7790001234567";

    @Test
    @DisplayName("Busca en cualquier parte del texto, sin distinguir mayúsculas")
    void contieneSinMayusculas() {
        assertThat(ComboBoxBusqueda.coincide(PRODUCTO, "yerba")).isTrue();
        assertThat(ComboBoxBusqueda.coincide(PRODUCTO, "MATE")).isTrue();
        assertThat(ComboBoxBusqueda.coincide(PRODUCTO, "yer001")).isTrue();
        assertThat(ComboBoxBusqueda.coincide(PRODUCTO, "7790001234567")).isTrue();
        assertThat(ComboBoxBusqueda.coincide(PRODUCTO, "arroz")).isFalse();
    }

    @Test
    @DisplayName("Ignora los acentos del texto y de la búsqueda")
    void sinAcentos() {
        assertThat(ComboBoxBusqueda.coincide("Azúcar común", "azucar comun")).isTrue();
        assertThat(ComboBoxBusqueda.coincide("Azucar", "azúcar")).isTrue();
    }

    @Test
    @DisplayName("Exige todas las palabras, en cualquier orden")
    void todasLasPalabras() {
        assertThat(ComboBoxBusqueda.coincide(PRODUCTO, "suave yerba")).isTrue();
        assertThat(ComboBoxBusqueda.coincide(PRODUCTO, "  mate   1kg ")).isTrue();
        assertThat(ComboBoxBusqueda.coincide(PRODUCTO, "yerba intensa")).isFalse();
    }

    @Test
    @DisplayName("Una búsqueda vacía coincide con todo; un texto null sólo con la búsqueda vacía")
    void vaciosYNulos() {
        assertThat(ComboBoxBusqueda.coincide(PRODUCTO, "")).isTrue();
        assertThat(ComboBoxBusqueda.coincide(PRODUCTO, null)).isTrue();
        assertThat(ComboBoxBusqueda.coincide(null, "")).isTrue();
        assertThat(ComboBoxBusqueda.coincide(null, "yerba")).isFalse();
    }
}
