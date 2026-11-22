package org.gestionComercio.backup;

import jakarta.annotation.PreDestroy;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.util.dialog.Dialogs;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Revisa cada pocos segundos si otra PC está restaurando la base. Si es así,
 * bloquea la aplicación con un aviso que sólo permite cerrarla: los datos en
 * pantalla dejan de valer y no se puede seguir operando hasta reiniciar.
 */
@Component
@RequiredArgsConstructor
public class MonitorRestauracion {

    private static final long INTERVALO_SEGUNDOS = 3;

    private final BloqueoRestauracion bloqueo;

    private final AtomicBoolean avisado = new AtomicBoolean();

    private ScheduledExecutorService executor;

    public synchronized void iniciar() {

        if (executor != null) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(tarea -> {
            Thread hilo = new Thread(tarea, "monitor-restauracion");
            hilo.setDaemon(true);
            return hilo;
        });

        executor.scheduleWithFixedDelay(this::revisar, INTERVALO_SEGUNDOS, INTERVALO_SEGUNDOS, TimeUnit.SECONDS);
    }

    @PreDestroy
    public synchronized void detener() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void revisar() {
        try {
            if (bloqueo.restauracionEnOtraPc() && avisado.compareAndSet(false, true)) {
                Platform.runLater(MonitorRestauracion::bloquear);
            }
        } catch (RuntimeException ex) {
            // Sin conexión (por ejemplo, la cortó la restauración): se vuelve a revisar.
        }
    }

    private static void bloquear() {

        Label mensaje = new Label("Otra PC está restaurando una copia de seguridad de la base de datos. "
                + "No se puede seguir trabajando: la aplicación se va a cerrar.\n\n"
                + "Cuando termine la restauración, vuelva a abrirla.");
        mensaje.setWrapText(true);
        mensaje.setMaxWidth(380);
        mensaje.getStyleClass().add("dialog-message");

        Alert alert = Dialogs.crear(Dialogs.Tipo.ADVERTENCIA, "Restauración en curso", mensaje,
                new ButtonType("Cerrar la aplicación", ButtonData.OK_DONE));

        // Se cierre como se cierre el aviso, la aplicación termina.
        alert.showAndWait();

        Platform.exit();
    }
}
