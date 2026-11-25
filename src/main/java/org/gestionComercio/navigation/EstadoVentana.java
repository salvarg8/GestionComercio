package org.gestionComercio.navigation;

import javafx.geometry.Rectangle2D;
import org.gestionComercio.config.WindowConfig;

import java.util.List;

/**
 * Cómo quedó la ventana principal: si estaba maximizada, y su posición y
 * tamaño sin maximizar (los que toma al restaurarla).
 */
public record EstadoVentana(boolean maximizada, double x, double y, double ancho, double alto) {

    /** Parte de la barra de título que tiene que verse en alguna pantalla para poder agarrar la ventana. */
    private static final double BARRA_TITULO_ALTO = 30;
    private static final double BARRA_TITULO_ANCHO_VISIBLE = 100;

    /** La primera vez la ventana abre maximizada; al restaurar toma el tamaño por defecto, centrada. */
    public static EstadoVentana inicial() {
        return new EstadoVentana(true, Double.NaN, Double.NaN, WindowConfig.WIDTH, WindowConfig.HEIGHT);
    }

    /**
     * Posición y tamaño sin maximizar para las pantallas actuales. Si la
     * posición guardada quedó fuera de todas (por ejemplo, se desconectó un
     * monitor) o no hay posición guardada, centra la ventana en la principal.
     *
     * @param pantallas zonas útiles de las pantallas conectadas (sin la barra de tareas)
     * @param principal zona útil de la pantalla principal
     */
    public Rectangle2D ubicacionEn(List<Rectangle2D> pantallas, Rectangle2D principal) {

        Rectangle2D guardada = new Rectangle2D(x, y, ancho, alto);
        if (posicionValida() && pantallas.stream().anyMatch(this::barraDeTituloVisibleEn)) {
            return guardada;
        }

        double anchoCentrado = acotar(ancho, WindowConfig.MIN_WIDTH, principal.getWidth());
        double altoCentrado = acotar(alto, WindowConfig.MIN_HEIGHT, principal.getHeight());

        return new Rectangle2D(
                principal.getMinX() + (principal.getWidth() - anchoCentrado) / 2,
                principal.getMinY() + (principal.getHeight() - altoCentrado) / 2,
                anchoCentrado,
                altoCentrado);
    }

    private boolean posicionValida() {
        return Double.isFinite(x) && Double.isFinite(y)
                && Double.isFinite(ancho) && Double.isFinite(alto)
                && ancho >= WindowConfig.MIN_WIDTH && alto >= WindowConfig.MIN_HEIGHT;
    }

    private boolean barraDeTituloVisibleEn(Rectangle2D pantalla) {
        double anchoVisible = Math.min(x + ancho, pantalla.getMaxX()) - Math.max(x, pantalla.getMinX());
        return anchoVisible >= BARRA_TITULO_ANCHO_VISIBLE
                && y >= pantalla.getMinY()
                && y + BARRA_TITULO_ALTO <= pantalla.getMaxY();
    }

    /** Entre el mínimo y lo que entra en la pantalla; si la pantalla es más chica que el mínimo, gana el mínimo. */
    private static double acotar(double valor, double minimo, double disponible) {
        double medida = Double.isFinite(valor) ? valor : minimo;
        return Math.max(minimo, Math.min(medida, disponible));
    }
}
