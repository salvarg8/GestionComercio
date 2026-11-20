package org.gestionComercio.validation;

import org.gestionComercio.entity.Producto;
import org.gestionComercio.util.CantidadUtils;

import java.math.BigDecimal;

/** Validación de la cantidad de un renglón de comprobante (venta o compra). */
final class CantidadRenglonValidacion {

    private CantidadRenglonValidacion() {
    }

    /**
     * @param campo  campo del error, por ejemplo {@code detalles[0].cantidad}
     * @param prefijo comienzo del mensaje, por ejemplo {@code "Renglón 1: "}
     * @param verbo  cómo se maneja el producto en el comprobante ("se vende", "se compra")
     * @return true si la cantidad no tiene errores
     */
    static boolean validar(
            BigDecimal cantidad,
            Producto producto,
            String campo,
            String prefijo,
            String verbo,
            ValidationResult result) {

        String nombre = "\"" + producto.getNombre() + "\"";

        if (cantidad == null) {
            result.addError(campo, prefijo + "debe indicar la cantidad de " + nombre + ".");
            return false;
        }

        if (cantidad.signum() <= 0) {
            result.addError(campo, prefijo + "la cantidad de " + nombre + " debe ser mayor que cero.");
            return false;
        }

        if (!CantidadUtils.escalaValida(cantidad)) {
            result.addError(campo, prefijo + "la cantidad de " + nombre + " admite como máximo "
                    + CantidadUtils.ESCALA + " decimales.");
            return false;
        }

        if (!CantidadUtils.dentroDelMaximo(cantidad)) {
            result.addError(campo, prefijo + "la cantidad de " + nombre + " es demasiado grande.");
            return false;
        }

        if (producto.getUnidadMedida() != null
                && !producto.getUnidadMedida().isFraccionable()
                && !CantidadUtils.esEntera(cantidad)) {
            result.addError(campo, prefijo + nombre + " " + verbo + " por "
                    + producto.getUnidadMedida().getDescripcion().toLowerCase()
                    + " y no admite decimales: ingrese un número entero.");
            return false;
        }

        return true;
    }

}
