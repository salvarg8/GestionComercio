package org.gestionComercio.enums;

/** De dónde toma esta PC la clave con que se cifra el certificado de ARCA. */
public enum OrigenClaveArca implements Descriptible {

    /** Variable de entorno ARCA_CLAVE_CERTIFICADO (o propiedad arca.clave-certificado): tiene prioridad. */
    VARIABLE_ENTORNO("Variable de entorno ARCA_CLAVE_CERTIFICADO"),

    /** Cargada desde la pantalla y guardada en el perfil de Windows del usuario de esta PC. */
    ESTA_PC("Guardada en esta PC"),

    SIN_CONFIGURAR("Sin configurar en esta PC");

    private final String descripcion;

    OrigenClaveArca(String descripcion) {
        this.descripcion = descripcion;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
