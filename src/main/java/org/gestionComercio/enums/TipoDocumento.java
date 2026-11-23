package org.gestionComercio.enums;

/** Tipo de documento, con su código de ARCA ({@code FEParamGetTiposDoc}). */
public enum TipoDocumento {

    DNI(96),
    CUIT(80),
    CUIL(86),
    PASAPORTE(94);

    /** Código de ARCA para un receptor sin identificar (consumidor final). */
    public static final int CODIGO_ARCA_SIN_IDENTIFICAR = 99;

    private final int codigoArca;

    TipoDocumento(int codigoArca) {
        this.codigoArca = codigoArca;
    }

    public int getCodigoArca() {
        return codigoArca;
    }

}
