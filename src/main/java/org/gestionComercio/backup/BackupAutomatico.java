package org.gestionComercio.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gestionComercio.repository.CajaRepository;
import org.gestionComercio.service.BackupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Copias automáticas. Sólo corre en la PC servidor ({@code BACKUP_AUTOMATICO=true}).
 *
 * <p>Cada pocos minutos revisa si hace falta una copia: si todavía no hay una
 * de hoy, o si se cerró alguna caja (en cualquier PC) después de la última.
 * Así la copia diaria se hace aunque la PC haya estado apagada a la hora
 * habitual. Conserva las últimas {@code backup.retencion} copias.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "backup.automatico", havingValue = "true")
public class BackupAutomatico {

    private final BackupService backupService;
    private final CajaRepository cajaRepository;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    public void revisar() {

        try {
            String motivo = motivo(
                    backupService.fechaUltimoBackupAutomatico(),
                    LocalDateTime.now(),
                    cajaRepository::existsByFechaCierreAfter);

            if (motivo == null) {
                return;
            }

            backupService.crearBackupAutomatico()
                    .ifPresent(archivo -> log.info("Copia automática ({}): {}", motivo, archivo));

        } catch (RuntimeException ex) {
            // Se reintenta en la próxima revisión.
            log.error("No se pudo hacer la copia automática", ex);
        }
    }

    /**
     * Motivo para hacer una copia ahora, o null si no hace falta.
     *
     * @param ultimo            fecha de la última copia automática
     * @param hayCierreDespues  indica si se cerró alguna caja después de una fecha
     */
    static String motivo(Optional<LocalDateTime> ultimo, LocalDateTime ahora,
                         Predicate<LocalDateTime> hayCierreDespues) {

        if (ultimo.isEmpty()) {
            return "primera copia";
        }
        if (ultimo.get().toLocalDate().isBefore(ahora.toLocalDate())) {
            return "copia diaria";
        }
        if (hayCierreDespues.test(ultimo.get())) {
            return "cierre de caja";
        }
        return null;
    }
}
