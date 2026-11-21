package org.gestionComercio.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DateUtilsTest {

    @Test
    @DisplayName("El fin del día tiene precisión de microsegundos, para que PostgreSQL no lo pase al día siguiente")
    void finDelDia() {
        LocalDate dia = LocalDate.of(2026, 11, 21);

        assertThat(DateUtils.finDelDia(dia)).isEqualTo(LocalDateTime.of(2026, 11, 21, 23, 59, 59, 999_999_000));
        assertThat(DateUtils.finDelDia(null)).isNull();
    }
}
