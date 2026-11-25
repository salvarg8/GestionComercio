package org.gestionComercio.navigation;

import javafx.geometry.Rectangle2D;
import org.gestionComercio.config.WindowConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EstadoVentanaTest {

    /** Zona útil de una pantalla 1920×1080 con la barra de tareas abajo. */
    private static final Rectangle2D PRINCIPAL = new Rectangle2D(0, 0, 1920, 1040);

    /** Segundo monitor a la derecha de la principal. */
    private static final Rectangle2D SECUNDARIA = new Rectangle2D(1920, 0, 1920, 1040);

    @Test
    @DisplayName("✓ la primera vez abre maximizada y al restaurar queda del tamaño por defecto, centrada")
    void estadoInicial() {
        EstadoVentana inicial = EstadoVentana.inicial();

        Rectangle2D ubicacion = inicial.ubicacionEn(List.of(PRINCIPAL), PRINCIPAL);

        assertTrue(inicial.maximizada());
        assertEquals(new Rectangle2D(
                (1920 - WindowConfig.WIDTH) / 2,
                (1040 - WindowConfig.HEIGHT) / 2,
                WindowConfig.WIDTH,
                WindowConfig.HEIGHT), ubicacion);
    }

    @Test
    @DisplayName("✓ si la posición guardada se ve en alguna pantalla, se respeta")
    void posicionGuardadaVisible() {
        EstadoVentana estado = new EstadoVentana(false, 2100, 50, 1300, 800);

        Rectangle2D ubicacion = estado.ubicacionEn(List.of(PRINCIPAL, SECUNDARIA), PRINCIPAL);

        assertEquals(new Rectangle2D(2100, 50, 1300, 800), ubicacion);
    }

    @Test
    @DisplayName("✓ si se desconectó el monitor donde estaba, se centra en la principal")
    void monitorDesconectado() {
        EstadoVentana estado = new EstadoVentana(false, 2100, 50, 1300, 800);

        Rectangle2D ubicacion = estado.ubicacionEn(List.of(PRINCIPAL), PRINCIPAL);

        assertEquals(new Rectangle2D((1920 - 1300) / 2.0, (1040 - 800) / 2.0, 1300, 800), ubicacion);
    }

    @Test
    @DisplayName("✓ si la barra de título queda fuera de la pantalla, se centra en la principal")
    void barraDeTituloFuera() {
        EstadoVentana arriba = new EstadoVentana(false, 100, -200, 1300, 800);
        EstadoVentana casiFueraALaIzquierda = new EstadoVentana(false, -1250, 100, 1300, 800);

        Rectangle2D centrada = new Rectangle2D((1920 - 1300) / 2.0, (1040 - 800) / 2.0, 1300, 800);

        assertEquals(centrada, arriba.ubicacionEn(List.of(PRINCIPAL), PRINCIPAL));
        assertEquals(centrada, casiFueraALaIzquierda.ubicacionEn(List.of(PRINCIPAL), PRINCIPAL));
    }

    @Test
    @DisplayName("✓ al centrar, el tamaño se ajusta a la pantalla sin bajar del mínimo")
    void tamanoAcotadoAlCentrar() {
        Rectangle2D notebook = new Rectangle2D(0, 0, 1366, 728);
        EstadoVentana grande = new EstadoVentana(false, 5000, 5000, 2500, 1400);

        Rectangle2D ubicacion = grande.ubicacionEn(List.of(notebook), notebook);

        assertEquals(new Rectangle2D(0, 0, 1366, 728), ubicacion);

        Rectangle2D chica = new Rectangle2D(0, 0, 1024, 600);
        Rectangle2D enPantallaChica = EstadoVentana.inicial().ubicacionEn(List.of(chica), chica);

        assertEquals(WindowConfig.MIN_WIDTH, enPantallaChica.getWidth());
        assertEquals(WindowConfig.MIN_HEIGHT, enPantallaChica.getHeight());
    }

    @Test
    @DisplayName("✓ un tamaño guardado menor al mínimo no se usa")
    void tamanoGuardadoMenorAlMinimo() {
        EstadoVentana estado = new EstadoVentana(false, 10, 10, 400, 300);

        Rectangle2D ubicacion = estado.ubicacionEn(List.of(PRINCIPAL), PRINCIPAL);

        assertEquals(WindowConfig.MIN_WIDTH, ubicacion.getWidth());
        assertEquals(WindowConfig.MIN_HEIGHT, ubicacion.getHeight());
    }
}
