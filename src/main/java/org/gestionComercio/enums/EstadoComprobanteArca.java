package org.gestionComercio.enums;

/**
 * Estado de un comprobante electrónico frente a ARCA.
 *
 * <pre>
 * PENDIENTE ──► ENVIADO ──► AUTORIZADO
 *    ▲  │          │
 *    │  │          └──────► RECHAZADO ──(reintento)──► PENDIENTE
 *    │  └─(venta anulada)─► CANCELADO
 *    └── (ARCA no lo recibió: se libera el número)
 * </pre>
 */
public enum EstadoComprobanteArca implements Descriptible {

    /** Todavía no se envió (o se reintenta): no tiene número. */
    PENDIENTE("Pendiente de CAE"),

    /**
     * Se envió con un número y no se sabe si ARCA lo autorizó (se cortó la
     * conexión). Antes de volver a enviarlo se consulta con FECompConsultar.
     */
    ENVIADO("Enviado, sin respuesta de ARCA"),

    /** ARCA lo autorizó: tiene número y CAE. */
    AUTORIZADO("Autorizado"),

    /** ARCA lo rechazó: no consumió número; se puede corregir y reintentar. */
    RECHAZADO("Rechazado por ARCA"),

    /** Se anuló la venta antes de enviar la factura: no se envía nunca. */
    CANCELADO("Cancelado");

    private final String descripcion;

    EstadoComprobanteArca(String descripcion) {
        this.descripcion = descripcion;
    }

    @Override
    public String getDescripcion() {
        return descripcion;
    }

    /** @return true si todavía hay que pedir (o confirmar) el CAE. */
    public boolean faltaAutorizar() {
        return this == PENDIENTE || this == ENVIADO || this == RECHAZADO;
    }

    @Override
    public String toString() {
        return descripcion;
    }

}
