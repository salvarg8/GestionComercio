package org.gestionComercio.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BackupAutomaticoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 21, 18, 0);

    @Test
    @DisplayName("sin copias previas hace la primera")
    void primeraCopia() {
        assertThat(BackupAutomatico.motivo(Optional.empty(), AHORA, f -> false)).isEqualTo("primera copia");
    }

    @Test
    @DisplayName("si la última copia es de otro día, hace la diaria")
    void copiaDiaria() {
        assertThat(BackupAutomatico.motivo(Optional.of(AHORA.minusDays(1).withHour(23)), AHORA, f -> false))
                .isEqualTo("copia diaria");
    }

    @Test
    @DisplayName("si se cerró una caja después de la última copia, hace otra")
    void cierreDeCaja() {
        LocalDateTime ultima = AHORA.minusHours(2);

        assertThat(BackupAutomatico.motivo(Optional.of(ultima), AHORA, ultima::equals)).isEqualTo("cierre de caja");
    }

    @Test
    @DisplayName("con copia de hoy y sin cierres posteriores no hace nada")
    void nadaQueHacer() {
        assertThat(BackupAutomatico.motivo(Optional.of(AHORA.minusHours(2)), AHORA, f -> false)).isNull();
    }
}
