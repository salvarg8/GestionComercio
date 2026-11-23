package org.gestionComercio.dto.arca;

import java.util.List;

/**
 * Resultado de la prueba de conexión con ARCA, paso por paso, para mostrarle
 * al usuario qué está bien y qué hay que corregir. Los pasos van en orden:
 * si uno falla, los siguientes quedan sin verificar.
 */
public record PruebaConexionArca(List<Paso> pasos) {

    public enum Resultado {
        CORRECTO,
        ERROR,
        /** No se llegó a verificar porque falló un paso anterior. */
        SIN_VERIFICAR
    }

    /**
     * @param nombre    qué se verifica ("Certificado", "Punto de venta 3", ...)
     * @param resultado cómo salió
     * @param detalle   lo encontrado, o el motivo del error y cómo corregirlo
     */
    public record Paso(String nombre, Resultado resultado, String detalle) {
    }

    public PruebaConexionArca {
        pasos = List.copyOf(pasos);
    }

    /** @return true si todos los pasos salieron bien. */
    public boolean exitosa() {
        return !pasos.isEmpty() && pasos.stream().allMatch(p -> p.resultado() == Resultado.CORRECTO);
    }

    /** El primer paso con error, o null si no hubo. */
    public Paso primerError() {
        return pasos.stream().filter(p -> p.resultado() == Resultado.ERROR).findFirst().orElse(null);
    }

}
