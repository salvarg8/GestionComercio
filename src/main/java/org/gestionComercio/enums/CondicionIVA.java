package org.gestionComercio.enums;

/**
 * Condición frente al IVA, con el código que usa ARCA para el receptor de un
 * comprobante ({@code FEParamGetCondicionIvaReceptor}, campo
 * {@code CondicionIVAReceptorId}, obligatorio desde la RG 5616).
 */
public enum CondicionIVA implements Descriptible {

    RESPONSABLE_INSCRIPTO("Responsable inscripto", 1),
    MONOTRIBUTISTA("Monotributista", 6),
    EXENTO("Exento", 4),
    CONSUMIDOR_FINAL("Consumidor final", 5),
    NO_CATEGORIZADO("Sujeto no categorizado", 7),
    MONOTRIBUTISTA_SOCIAL("Monotributista social", 13),
    MONOTRIBUTO_TRABAJADOR_PROMOVIDO("Monotributo trabajador independiente promovido", 16),
    IVA_NO_ALCANZADO("IVA no alcanzado", 15),
    IVA_LIBERADO("IVA liberado (Ley 19.640)", 10),
    PROVEEDOR_EXTERIOR("Proveedor del exterior", 8),
    CLIENTE_EXTERIOR("Cliente del exterior", 9);

    private final String descripcion;

    private final int codigoArca;

    CondicionIVA(String descripcion, int codigoArca) {
        this.descripcion = descripcion;
        this.codigoArca = codigoArca;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    public int getCodigoArca() {
        return codigoArca;
    }

    /** Condiciones posibles para la empresa que emite los comprobantes. */
    public static CondicionIVA[] deEmisor() {
        return new CondicionIVA[]{RESPONSABLE_INSCRIPTO, MONOTRIBUTISTA, EXENTO};
    }

    /** @return true si la empresa puede tener esta condición (emite comprobantes). */
    public boolean esDeEmisor() {
        return this == RESPONSABLE_INSCRIPTO || this == MONOTRIBUTISTA || this == EXENTO;
    }

    /**
     * @return true si un responsable inscripto le emite factura A (responsables
     *         inscriptos y monotributistas); al resto, factura B
     */
    public boolean recibeFacturaA() {
        return this == RESPONSABLE_INSCRIPTO
                || this == MONOTRIBUTISTA
                || this == MONOTRIBUTISTA_SOCIAL
                || this == MONOTRIBUTO_TRABAJADOR_PROMOVIDO;
    }

    /** @return true si el cliente con esta condición se identifica con CUIT. */
    public boolean requiereCuit() {
        return recibeFacturaA() || this == EXENTO || this == IVA_NO_ALCANZADO || this == IVA_LIBERADO;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
