package org.gestionComercio.arca;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Datos que se intercambian con el WSFEv1. */
public final class Wsfe {

    /** Concepto: productos. */
    public static final int CONCEPTO_PRODUCTOS = 1;

    /** Moneda: pesos. */
    public static final String MONEDA_PESOS = "PES";

    /** Error de ARCA "no existen datos" (consultas sin resultado). */
    public static final int ERROR_SIN_RESULTADOS = 602;

    /** Errores de autenticación: el ticket no sirve (vencido, de otro CUIT o sin relación). */
    public static final List<Integer> ERRORES_AUTENTICACION = List.of(600, 601);

    private Wsfe() {
    }

    /** Bloque Auth de cada llamada. */
    public record Auth(String token, String sign, long cuit) {

        @Override
        public String toString() {
            return "Auth[cuit=" + cuit + "]";
        }
    }

    public record AlicuotaIva(int id, BigDecimal baseImponible, BigDecimal importe) {
    }

    /** Comprobante asociado (CbtesAsoc), obligatorio en notas de crédito. */
    public record ComprobanteAsociado(int tipo, int puntoVenta, long numero, long cuit, LocalDate fecha) {
    }

    /** Un comprobante para FECAESolicitar (CantReg = 1, CbteDesde = CbteHasta = numero). */
    public record SolicitudCae(
            int puntoVenta,
            int tipoComprobante,
            long numero,
            LocalDate fecha,
            int docTipo,
            long docNro,
            int condicionIvaReceptor,
            BigDecimal importeTotal,
            BigDecimal importeNoGravado,
            BigDecimal importeNeto,
            BigDecimal importeExento,
            BigDecimal importeTributos,
            BigDecimal importeIva,
            List<AlicuotaIva> alicuotas,
            ComprobanteAsociado asociado
    ) {
    }

    public record Mensaje(int codigo, String texto) {

        @Override
        public String toString() {
            return codigo + ": " + texto;
        }
    }

    /**
     * Respuesta de FECAESolicitar.
     *
     * @param resultado     A (aprobado), R (rechazado) o null si ARCA no procesó el
     *                      comprobante (sólo errores generales)
     * @param observaciones observaciones del comprobante (con A son advertencias; con R, el motivo)
     * @param errores       errores generales del pedido
     */
    public record RespuestaCae(
            String resultado,
            String cae,
            LocalDate vencimientoCae,
            List<Mensaje> observaciones,
            List<Mensaje> errores
    ) {

        public boolean aprobado() {
            return "A".equals(resultado) && cae != null && !cae.isBlank();
        }

        public boolean errorDeAutenticacion() {
            return errores.stream().anyMatch(e -> ERRORES_AUTENTICACION.contains(e.codigo()));
        }
    }

    /** Un comprobante ya emitido, de FECompConsultar. */
    public record ComprobanteEmitido(
            int puntoVenta,
            int tipoComprobante,
            long numero,
            LocalDate fecha,
            int docTipo,
            long docNro,
            BigDecimal importeTotal,
            String resultado,
            String cae,
            LocalDate vencimientoCae
    ) {
    }

    /** Estado de los servidores de ARCA (FEDummy). */
    public record EstadoServidores(String appServer, String dbServer, String authServer) {

        public boolean todosOk() {
            return "OK".equalsIgnoreCase(appServer)
                    && "OK".equalsIgnoreCase(dbServer)
                    && "OK".equalsIgnoreCase(authServer);
        }
    }

}
