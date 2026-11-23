package org.gestionComercio.arca;

import java.util.Optional;

/**
 * Dónde se guarda, en cada PC, la clave del certificado de ARCA cargada desde
 * la pantalla. Nunca en la base: así la base y sus copias de seguridad no
 * alcanzan para usar el certificado.
 */
public interface AlmacenClaveLocal {

    Optional<String> leer();

    void guardar(String clave);

    void borrar();

}
