package org.gestionComercio.dto.arca;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gestionComercio.arca.QrArca;
import org.gestionComercio.enums.AmbienteArca;
import org.gestionComercio.enums.EstadoComprobanteArca;
import org.gestionComercio.enums.TipoComprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Factura o nota de crédito electrónica de una venta, con su estado frente a ARCA. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComprobanteArcaDto {

    private Long id;

    private Long ventaId;

    /** Fecha de la venta. */
    private LocalDateTime ventaFecha;

    /** Cliente de la venta, o consumidor final. */
    private String clienteNombre;

    private TipoComprobante tipoComprobante;

    private EstadoComprobanteArca estado;

    private AmbienteArca ambiente;

    private String cuitEmisor;

    private Integer puntoVenta;

    /** Null mientras ARCA no le asignó número. */
    private Long numero;

    private LocalDate fecha;

    private String cae;

    private LocalDate vencimientoCae;

    private Integer docTipo;

    private Long docNro;

    /** Código de ARCA de la condición frente al IVA del receptor. */
    private Integer condicionIvaReceptor;

    private BigDecimal importeTotal;

    private BigDecimal importeNeto;

    private BigDecimal importeIva;

    private BigDecimal importeExento;

    @Builder.Default
    private List<IvaComprobanteArcaDto> alicuotas = new ArrayList<>();

    /** Observaciones o motivo de rechazo de ARCA, o el último error de comunicación. */
    private String observaciones;

    private LocalDateTime fechaProceso;

    /** La factura que anula (sólo en notas de crédito), formateada. */
    private String comprobanteAsociado;

    public boolean isAutorizado() {
        return estado == EstadoComprobanteArca.AUTORIZADO;
    }

    /** "Factura B N° 0003-00000123", o "Factura B (pendiente de CAE)" si todavía no tiene número. */
    public String getComprobante() {

        if (tipoComprobante == null) {
            return "";
        }

        if (numero == null) {
            return tipoComprobante.getDescripcion() + " (" + estadoParaMostrar() + ")";
        }

        return TipoComprobante.formatear(tipoComprobante, puntoVenta, numero);
    }

    /** "pendiente de CAE", "rechazado por ARCA", ...: la descripción con la inicial en minúscula. */
    private String estadoParaMostrar() {

        if (estado == null) {
            return "sin número";
        }

        String descripcion = estado.getDescripcion();
        return Character.toLowerCase(descripcion.charAt(0)) + descripcion.substring(1);
    }

    /** URL del código QR de ARCA; null si no está autorizado. */
    public String getQrUrl() {

        if (!isAutorizado() || cuitEmisor == null || numero == null || fecha == null) {
            return null;
        }

        return QrArca.url(new QrArca.Datos(
                fecha,
                Long.parseLong(cuitEmisor),
                puntoVenta,
                tipoComprobante.getCodigoArca(),
                numero,
                importeTotal,
                docTipo == null ? 0 : docTipo,
                docNro == null ? 0 : docNro,
                cae));
    }

}
