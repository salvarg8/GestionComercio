package org.gestionComercio.enums;

/**
 * Ambiente de los web services de ARCA. Son independientes: cada uno usa su
 * propio certificado y su propia numeración.
 */
public enum AmbienteArca implements Descriptible {

    HOMOLOGACION("Homologación (pruebas)",
            "https://wsaahomo.afip.gov.ar/ws/services/LoginCms",
            "https://wswhomo.afip.gov.ar/wsfev1/service.asmx"),

    PRODUCCION("Producción",
            "https://wsaa.afip.gov.ar/ws/services/LoginCms",
            "https://servicios1.afip.gov.ar/wsfev1/service.asmx");

    private final String descripcion;
    private final String urlWsaa;
    private final String urlWsfe;

    AmbienteArca(String descripcion, String urlWsaa, String urlWsfe) {
        this.descripcion = descripcion;
        this.urlWsaa = urlWsaa;
        this.urlWsfe = urlWsfe;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    public String getUrlWsaa() {
        return urlWsaa;
    }

    public String getUrlWsfe() {
        return urlWsfe;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
