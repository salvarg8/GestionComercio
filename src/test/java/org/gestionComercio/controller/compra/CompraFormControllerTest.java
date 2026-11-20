package org.gestionComercio.controller.compra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompraFormControllerTest {

    @Test
    @DisplayName("Sólo un texto de ocho o más dígitos se sugiere como código de barras al cargar el artículo")
    void pareceCodigoDeBarras() {
        assertThat(CompraFormController.pareceCodigoDeBarras("7790895000997")).isTrue();
        assertThat(CompraFormController.pareceCodigoDeBarras("12345678")).isTrue();

        assertThat(CompraFormController.pareceCodigoDeBarras("1234")).isFalse();
        assertThat(CompraFormController.pareceCodigoDeBarras("Gaseosa 2L")).isFalse();
        assertThat(CompraFormController.pareceCodigoDeBarras(null)).isFalse();
    }
}
