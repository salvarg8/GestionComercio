package org.gestionComercio.arca;

import org.gestionComercio.enums.OrigenClaveArca;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Clave con que se cifra el certificado de ARCA guardado en la base. Tiene
 * que ser la misma en todas las PC. Se toma de la variable de entorno
 * ARCA_CLAVE_CERTIFICADO si está definida; si no, de la cargada desde la
 * pantalla en esta PC ({@link AlmacenClaveLocal}).
 */
@Component
public class ClaveCertificadoArca {

    /** Largo mínimo de una clave cargada desde la pantalla. */
    public static final int LARGO_MINIMO = 8;

    private final ArcaProperties properties;
    private final AlmacenClaveLocal almacen;

    public ClaveCertificadoArca(ArcaProperties properties, AlmacenClaveLocal almacen) {
        this.properties = properties;
        this.almacen = almacen;
    }

    public OrigenClaveArca origen() {

        if (properties.tieneClaveCertificado()) {
            return OrigenClaveArca.VARIABLE_ENTORNO;
        }

        return almacen.leer().isPresent() ? OrigenClaveArca.ESTA_PC : OrigenClaveArca.SIN_CONFIGURAR;
    }

    public boolean configurada() {
        return origen() != OrigenClaveArca.SIN_CONFIGURAR;
    }

    /** @return la clave, o vacío si esta PC no la tiene */
    public Optional<char[]> obtener() {

        if (properties.tieneClaveCertificado()) {
            return Optional.of(properties.claveCertificado().toCharArray());
        }

        return almacen.leer().map(String::toCharArray);
    }

    /** Guarda la clave en esta PC (la variable de entorno, si está, sigue teniendo prioridad). */
    public void guardarEnEstaPc(char[] clave) {
        almacen.guardar(new String(clave));
    }

    public void borrarDeEstaPc() {
        almacen.borrar();
    }

}
