package org.gestionComercio.service.impl.arca;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.arca.ArcaProperties;
import org.gestionComercio.arca.ReglasFacturacion;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.entity.ComprobanteArca;
import org.gestionComercio.entity.IvaComprobanteArca;
import org.gestionComercio.entity.Venta;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoComprobanteArca;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.repository.ComprobanteArcaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Comprobantes electrónicos que nacen con la venta o con su anulación. Corre
 * dentro de la transacción de la venta y no llama a ARCA: deja los
 * comprobantes PENDIENTES, y {@code FacturacionElectronicaService.autorizar}
 * pide el CAE una vez confirmada la venta.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class EmisionComprobantesArca {

    private final ConfiguracionArcaVigente configuracion;
    private final ComprobanteArcaRepository repository;
    private final ArcaProperties properties;

    /**
     * Controla que se pueda emitir la factura de la venta y la deja pendiente de CAE.
     *
     * @throws BusinessException si la facturación no está habilitada o la factura no
     *         corresponde al cliente (letra, documento, monto a consumidor final)
     */
    public ComprobanteArca registrarFactura(Venta venta) {

        ContextoArca emisor = configuracion.emisor();
        ReglasFacturacion.Receptor receptor = receptor(venta.getCliente());

        List<String> problemas = ReglasFacturacion.problemas(venta.getTipoComprobante(), emisor.condicionIva(),
                receptor, venta.getTotal(), properties.montoIdentificacionConsumidorFinal());

        if (!problemas.isEmpty()) {
            throw new BusinessException(String.join("\n", problemas));
        }

        ComprobanteArca factura = repository.save(ComprobanteArca.builder()
                .venta(venta)
                .tipoComprobante(venta.getTipoComprobante())
                .estado(EstadoComprobanteArca.PENDIENTE)
                .ambiente(emisor.ambiente())
                .cuitEmisor(emisor.cuit())
                .puntoVenta(emisor.puntoVenta())
                .build());

        venta.getComprobantesArca().add(factura);
        return factura;
    }

    /**
     * Al anular una venta facturada: si la factura ya se autorizó, deja
     * pendiente la nota de crédito que la anula; si todavía no se envió (o
     * fue rechazada), la cancela para que no se envíe nunca.
     *
     * @throws BusinessException si la factura se envió y no se sabe si ARCA la
     *         autorizó: hay que reintentar la autorización antes de anular
     */
    public void anularVenta(Venta venta) {

        ComprobanteArca factura = venta.getFacturaArca();

        if (factura == null) {
            return;
        }

        switch (factura.getEstado()) {

            case ENVIADO -> throw new BusinessException("La " + descripcion(factura) + " se envió a ARCA y todavía "
                    + "no se sabe si se autorizó. Reintente la autorización antes de anular la venta.");

            case PENDIENTE, RECHAZADO -> {
                factura.setEstado(EstadoComprobanteArca.CANCELADO);
                factura.setObservaciones("Venta anulada antes de autorizar la factura: no se envía a ARCA.");
            }

            case AUTORIZADO -> {
                if (venta.getNotaCreditoArca() == null) {
                    venta.getComprobantesArca().add(repository.save(notaDeCredito(factura)));
                }
            }

            case CANCELADO -> {
                // Nada que hacer.
            }

            default -> throw new IllegalStateException("Estado desconocido: " + factura.getEstado());
        }
    }

    /** Nota de crédito por el total de la factura, con su mismo receptor, importes y alícuotas. */
    private static ComprobanteArca notaDeCredito(ComprobanteArca factura) {

        return ComprobanteArca.builder()
                .venta(factura.getVenta())
                .tipoComprobante(factura.getTipoComprobante().notaDeCredito())
                .comprobanteAsociado(factura)
                .estado(EstadoComprobanteArca.PENDIENTE)
                .ambiente(factura.getAmbiente())
                .cuitEmisor(factura.getCuitEmisor())
                .puntoVenta(factura.getPuntoVenta())
                .docTipo(factura.getDocTipo())
                .docNro(factura.getDocNro())
                .condicionIvaReceptor(factura.getCondicionIvaReceptor())
                .importeTotal(factura.getImporteTotal())
                .importeNeto(factura.getImporteNeto())
                .importeIva(factura.getImporteIva())
                .importeExento(factura.getImporteExento())
                .alicuotas(factura.getAlicuotas().stream()
                        .map(a -> new IvaComprobanteArca(a.getAlicuotaArca(), a.getBaseImponible(), a.getImporte()))
                        .collect(Collectors.toCollection(ArrayList::new)))
                .build();
    }

    /** El receptor ante ARCA del cliente de la venta; sin cliente, consumidor final. */
    static ReglasFacturacion.Receptor receptor(Cliente cliente) {

        if (cliente == null) {
            return ReglasFacturacion.Receptor.CONSUMIDOR_FINAL_SIN_IDENTIFICAR;
        }

        CondicionIVA condicion = cliente.getCondicionIva() == null
                ? CondicionIVA.CONSUMIDOR_FINAL
                : cliente.getCondicionIva();

        return ReglasFacturacion.receptor(cliente.getTipoDocumento(), cliente.getNumeroDocumento(), condicion);
    }

    private static String descripcion(ComprobanteArca c) {
        return TipoComprobante.formatear(c.getTipoComprobante(), c.getPuntoVenta(), c.getNumero()).toLowerCase();
    }

}
