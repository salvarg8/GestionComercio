package org.gestionComercio.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.gestionComercio.arca.ArcaComunicacionException;
import org.gestionComercio.arca.ArcaException;
import org.gestionComercio.arca.ImportesArca;
import org.gestionComercio.arca.ReglasFacturacion;
import org.gestionComercio.arca.Wsfe;
import org.gestionComercio.arca.WsfeCliente;
import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.entity.ComprobanteArca;
import org.gestionComercio.entity.IvaComprobanteArca;
import org.gestionComercio.entity.Venta;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoComprobanteArca;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.mapper.ComprobanteArcaMapper;
import org.gestionComercio.repository.BloqueoArcaRepository;
import org.gestionComercio.repository.ClienteRepository;
import org.gestionComercio.repository.ComprobanteArcaRepository;
import org.gestionComercio.repository.VentaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.FacturacionElectronicaService;
import org.gestionComercio.service.impl.arca.ConfiguracionArcaVigente;
import org.gestionComercio.service.impl.arca.ContextoArca;
import org.gestionComercio.service.impl.arca.TicketAccesoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cada comprobante se autoriza así, con la numeración de su punto de venta y
 * tipo bloqueada en la base (advisory lock de una transacción que sólo
 * sostiene el bloqueo) mientras dura todo el proceso:
 *
 * <ol>
 *   <li>Confirma en ARCA los ENVIADOS sin respuesta de esa numeración.</li>
 *   <li>Consulta el último número autorizado.</li>
 *   <li>Guarda y confirma (transacción propia) el número siguiente y los
 *       importes: el comprobante pasa a ENVIADO.</li>
 *   <li>Pide el CAE y guarda la respuesta (transacción propia).</li>
 * </ol>
 *
 * <p>Las escrituras van en transacciones propias para que queden confirmadas
 * aunque después se corte la conexión o se cierre la aplicación. El bloqueo
 * se libera solo cuando termina la transacción que lo sostiene (o se cae su
 * conexión).
 */
@Slf4j
@Service
public class FacturacionElectronicaServiceImpl implements FacturacionElectronicaService {

    private static final ZoneId ZONA_ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static final Set<EstadoComprobanteArca> PENDIENTES =
            EnumSet.of(EstadoComprobanteArca.PENDIENTE, EstadoComprobanteArca.ENVIADO);

    private static final Set<EstadoComprobanteArca> SIN_AUTORIZAR = EnumSet.of(
            EstadoComprobanteArca.PENDIENTE, EstadoComprobanteArca.ENVIADO, EstadoComprobanteArca.RECHAZADO);

    private final ComprobanteArcaRepository comprobanteRepository;
    private final VentaRepository ventaRepository;
    private final ClienteRepository clienteRepository;
    private final BloqueoArcaRepository bloqueo;
    private final ConfiguracionArcaVigente configuracion;
    private final TicketAccesoService tickets;
    private final WsfeCliente wsfe;
    private final ComprobanteArcaMapper mapper;
    private final SesionUsuario sesionUsuario;

    /** Sostiene el bloqueo de la numeración (o se suma a la transacción en curso). */
    private final TransactionTemplate transaccion;

    /** Escrituras y lecturas que se confirman por su cuenta. */
    private final TransactionTemplate transaccionNueva;

    public FacturacionElectronicaServiceImpl(ComprobanteArcaRepository comprobanteRepository,
                                             VentaRepository ventaRepository,
                                             ClienteRepository clienteRepository,
                                             BloqueoArcaRepository bloqueo,
                                             ConfiguracionArcaVigente configuracion,
                                             TicketAccesoService tickets,
                                             WsfeCliente wsfe,
                                             ComprobanteArcaMapper mapper,
                                             SesionUsuario sesionUsuario,
                                             PlatformTransactionManager transactionManager) {
        this.comprobanteRepository = comprobanteRepository;
        this.ventaRepository = ventaRepository;
        this.clienteRepository = clienteRepository;
        this.bloqueo = bloqueo;
        this.configuracion = configuracion;
        this.tickets = tickets;
        this.wsfe = wsfe;
        this.mapper = mapper;
        this.sesionUsuario = sesionUsuario;

        this.transaccion = new TransactionTemplate(transactionManager);
        this.transaccionNueva = new TransactionTemplate(transactionManager);
        this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean estaHabilitada() {
        sesionUsuario.requerirAlgunPermiso(PermisoCodigo.VENTA_CREAR, PermisoCodigo.VENTA_VER,
                PermisoCodigo.FACTURACION_REINTENTAR);
        return configuracion.habilitada();
    }

    @Override
    public TipoComprobante tipoFactura(Long clienteId) {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_CREAR);

        return transaccionNueva.execute(s -> {

            ContextoArca emisor = configuracion.emisor();

            CondicionIVA receptor = clienteId == null
                    ? CondicionIVA.CONSUMIDOR_FINAL
                    : clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new EntityNotFoundException("No existe un cliente con ID: " + clienteId))
                    .getCondicionIva();

            return ReglasFacturacion.tipoFactura(emisor.condicionIva(), receptor);
        });
    }

    @Override
    public List<ComprobanteArcaDto> autorizar(Long ventaId) {

        sesionUsuario.requerirAlgunPermiso(PermisoCodigo.VENTA_CREAR, PermisoCodigo.VENTA_ANULAR,
                PermisoCodigo.FACTURACION_REINTENTAR);

        if (ventaId == null) {
            throw new BusinessException("La venta es obligatoria.");
        }

        List<Long> ids = transaccionNueva.execute(s -> {

            if (!ventaRepository.existsById(ventaId)) {
                throw new EntityNotFoundException("No existe una venta con ID: " + ventaId);
            }

            return comprobanteRepository.findByVentaIdOrderByIdAsc(ventaId).stream()
                    .filter(c -> c.getEstado().faltaAutorizar())
                    .map(ComprobanteArca::getId)
                    .toList();
        });

        if (!ids.isEmpty()) {

            ContextoArca contexto = configuracion.cargar(true);

            for (Long id : ids) {
                autorizarComprobante(contexto, id, true);
            }
        }

        return transaccionNueva.execute(s -> mapper.toDtoList(comprobanteRepository.findByVentaIdOrderByIdAsc(ventaId)));
    }

    @Override
    public List<ComprobanteArcaDto> autorizarPendientes() {

        sesionUsuario.requerirPermiso(PermisoCodigo.FACTURACION_REINTENTAR);

        List<Long> ids = transaccionNueva.execute(s -> comprobanteRepository.findByEstados(PENDIENTES).stream()
                .map(ComprobanteArca::getId)
                .toList());

        if (ids.isEmpty()) {
            return List.of();
        }

        ContextoArca contexto = configuracion.cargar(true);

        for (Long id : ids) {
            try {
                autorizarComprobante(contexto, id, false);
            } catch (ArcaComunicacionException e) {
                // Sin conexión, los demás tampoco van a poder enviarse.
                log.warn("Se interrumpe la autorización de pendientes: {}", e.getMessage());
                registrarObservacion(id, e.getMessage());
                break;
            } catch (BusinessException e) {
                log.warn("No se pudo autorizar el comprobante {}: {}", id, e.getMessage());
                registrarObservacion(id, e.getMessage());
            }
        }

        return transaccionNueva.execute(s -> mapper.toDtoList(comprobanteRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(ComprobanteArca::getId))
                .toList()));
    }

    @Override
    public List<ComprobanteArcaDto> listarSinAutorizar() {

        sesionUsuario.requerirAlgunPermiso(PermisoCodigo.FACTURACION_REINTENTAR, PermisoCodigo.VENTA_VER);

        return transaccionNueva.execute(s -> mapper.toDtoList(comprobanteRepository.findByEstados(SIN_AUTORIZAR)));
    }

    // ------------------------------------------------------------------
    // Autorización de un comprobante
    // ------------------------------------------------------------------

    /** Lo que se necesita saber del comprobante antes de bloquear su numeración. */
    private record Situacion(TipoComprobante tipo, EstadoComprobanteArca estado) {
    }

    /** Lo que se guardó del comprobante enviado sin respuesta, para compararlo con ARCA. */
    private record Enviado(String comprobante, int puntoVenta, long numero, LocalDate fecha,
                           BigDecimal importeTotal, long docNro) {
    }

    /**
     * @param reintentarRechazado true para volver a enviar un comprobante rechazado
     */
    private void autorizarComprobante(ContextoArca contexto, Long id, boolean reintentarRechazado) {

        Situacion situacion = transaccionNueva.execute(s -> {

            ComprobanteArca c = comprobanteRepository.findById(id)
                    .orElseThrow(() -> new EntityNotFoundException("No existe el comprobante electrónico " + id));

            if (c.getEstado() == EstadoComprobanteArca.RECHAZADO && reintentarRechazado) {
                c.setEstado(EstadoComprobanteArca.PENDIENTE);
            }

            return new Situacion(c.getTipoComprobante(), c.getEstado());
        });

        if (!PENDIENTES.contains(situacion.estado())) {
            return;
        }

        int tipoArca = situacion.tipo().getCodigoArca();

        transaccion.executeWithoutResult(s -> {

            bloqueo.bloquearNumeracion(contexto.puntoVenta(), tipoArca);

            confirmarEnviados(contexto, situacion.tipo());

            Wsfe.Auth auth = tickets.obtener(contexto);
            long ultimo = wsfe.ultimoAutorizado(contexto.ambiente(), auth, contexto.puntoVenta(), tipoArca);

            Wsfe.SolicitudCae solicitud = transaccionNueva.execute(t -> preparar(contexto, id, ultimo + 1));

            if (solicitud == null) {
                return;
            }

            Wsfe.RespuestaCae respuesta;

            try {
                respuesta = wsfe.solicitarCae(contexto.ambiente(), auth, solicitud);
            } catch (ArcaException e) {
                // No se sabe si ARCA lo recibió: queda ENVIADO y se confirma en el próximo intento.
                log.warn("Sin respuesta de ARCA al pedir el CAE del comprobante {} (N° {}): {}",
                        id, solicitud.numero(), e.getMessage());
                registrarObservacion(id, "No se obtuvo respuesta de ARCA al pedir el CAE. Se verificará en el "
                        + "próximo intento. Detalle: " + e.getMessage());
                return;
            }

            transaccionNueva.executeWithoutResult(t -> aplicar(id, respuesta));

            if (respuesta.errorDeAutenticacion()) {
                tickets.invalidar(contexto);
            }
        });
    }

    /**
     * Asigna el número, calcula los importes y deja el comprobante ENVIADO.
     * Bloquea la venta: así no se cruza con su anulación.
     *
     * @return el pedido para ARCA, o null si el comprobante ya no está pendiente
     */
    private Wsfe.SolicitudCae preparar(ContextoArca contexto, Long id, long numero) {

        ComprobanteArca c = comprobanteRepository.findById(id).orElseThrow();
        Venta venta = ventaRepository.findParaModificar(c.getVenta().getId()).orElseThrow();

        if (c.getEstado() != EstadoComprobanteArca.PENDIENTE) {
            return null;
        }

        if (c.getTipoComprobante().esFactura() && venta.getEstadoVenta() == EstadoVenta.ANULADA) {
            c.setEstado(EstadoComprobanteArca.CANCELADO);
            return null;
        }

        Wsfe.ComprobanteAsociado asociado = null;

        if (c.getTipoComprobante().esFactura()) {
            completarFactura(c, venta);
        } else {
            ComprobanteArca factura = c.getComprobanteAsociado();

            if (factura.getEstado() != EstadoComprobanteArca.AUTORIZADO) {
                throw new BusinessException("La nota de crédito no se puede autorizar: la factura que anula no está "
                        + "autorizada.");
            }

            asociado = new Wsfe.ComprobanteAsociado(factura.getTipoComprobante().getCodigoArca(),
                    factura.getPuntoVenta(), factura.getNumero(), Long.parseLong(factura.getCuitEmisor()),
                    factura.getFecha());
        }

        c.setAmbiente(contexto.ambiente());
        c.setCuitEmisor(contexto.cuit());
        c.setPuntoVenta(contexto.puntoVenta());
        c.setNumero(numero);
        c.setFecha(LocalDate.now(ZONA_ARGENTINA));
        c.setEstado(EstadoComprobanteArca.ENVIADO);
        c.setFechaProceso(LocalDateTime.now());
        c.setObservaciones(null);

        return new Wsfe.SolicitudCae(
                c.getPuntoVenta(),
                c.getTipoComprobante().getCodigoArca(),
                numero,
                c.getFecha(),
                c.getDocTipo(),
                c.getDocNro(),
                c.getCondicionIvaReceptor(),
                c.getImporteTotal(),
                BigDecimal.ZERO,
                c.getImporteNeto(),
                c.getImporteExento(),
                BigDecimal.ZERO,
                c.getImporteIva(),
                c.getAlicuotas().stream()
                        .map(a -> new Wsfe.AlicuotaIva(a.getAlicuotaArca(), a.getBaseImponible(), a.getImporte()))
                        .toList(),
                asociado);
    }

    /** Receptor e importes de la factura, con los datos actuales del cliente y los renglones de la venta. */
    private static void completarFactura(ComprobanteArca c, Venta venta) {

        ReglasFacturacion.Receptor receptor = receptor(venta);

        ImportesArca.Importes importes = ImportesArca.calcular(
                c.getTipoComprobante(),
                venta.getDetalles().stream()
                        .map(d -> new ImportesArca.Renglon(d.getProducto().getAlicuotaIVA(), d.getSubtotal()))
                        .toList(),
                venta.getDescuento());

        if (importes.total().compareTo(venta.getTotal()) != 0) {
            throw new IllegalStateException("El total calculado para ARCA (" + importes.total()
                    + ") no coincide con el de la venta (" + venta.getTotal() + ").");
        }

        c.setDocTipo(receptor.docTipo());
        c.setDocNro(receptor.docNro());
        c.setCondicionIvaReceptor(receptor.condicionIva().getCodigoArca());
        c.setImporteTotal(importes.total());
        c.setImporteNeto(importes.neto());
        c.setImporteIva(importes.iva());
        c.setImporteExento(importes.exento());
        c.getAlicuotas().clear();
        importes.alicuotas().forEach(a -> c.getAlicuotas().add(
                new IvaComprobanteArca(a.id(), a.baseImponible(), a.importe())));
    }

    private static ReglasFacturacion.Receptor receptor(Venta venta) {

        if (venta.getCliente() == null) {
            return ReglasFacturacion.Receptor.CONSUMIDOR_FINAL_SIN_IDENTIFICAR;
        }

        return ReglasFacturacion.receptor(venta.getCliente().getTipoDocumento(),
                venta.getCliente().getNumeroDocumento(),
                venta.getCliente().getCondicionIva());
    }

    /** Guarda la respuesta de FECAESolicitar. */
    private void aplicar(Long id, Wsfe.RespuestaCae r) {

        ComprobanteArca c = comprobanteRepository.findById(id).orElseThrow();
        c.setFechaProceso(LocalDateTime.now());

        String mensajes = unir(r.observaciones(), r.errores());

        if (r.aprobado()) {
            c.setEstado(EstadoComprobanteArca.AUTORIZADO);
            c.setCae(r.cae());
            c.setVencimientoCae(r.vencimientoCae());
            c.setObservaciones(mensajes);
            log.info("ARCA autorizó {} con CAE {}", formatear(c), r.cae());
            return;
        }

        if ("A".equals(r.resultado())) {
            // Aprobado sin CAE: no debería pasar. Queda ENVIADO para confirmarlo con FECompConsultar.
            c.setObservaciones("ARCA aprobó el comprobante sin informar el CAE; se verificará en el próximo intento.");
            return;
        }

        // Rechazado o no procesado: ARCA no consumió el número.
        c.setNumero(null);

        if (r.errorDeAutenticacion()) {
            c.setEstado(EstadoComprobanteArca.PENDIENTE);
            c.setObservaciones("ARCA rechazó el ticket de acceso; se pedirá uno nuevo en el próximo intento. "
                    + "Detalle: " + mensajes);
            return;
        }

        c.setEstado(EstadoComprobanteArca.RECHAZADO);
        c.setObservaciones(mensajes == null ? "ARCA rechazó el comprobante sin indicar el motivo." : mensajes);
        log.info("ARCA rechazó el comprobante {} ({}): {}", id, c.getTipoComprobante(), c.getObservaciones());
    }

    /**
     * Confirma en ARCA los comprobantes de esa numeración que se enviaron sin
     * respuesta: si ARCA los autorizó, toma el CAE; si no los tiene, libera el
     * número para volver a enviarlos. Se llama con la numeración bloqueada.
     *
     * @throws ArcaException si no se pudo consultar: no se puede seguir sin riesgo de duplicar
     */
    private void confirmarEnviados(ContextoArca contexto, TipoComprobante tipo) {

        List<Long> enviados = transaccionNueva.execute(s -> comprobanteRepository.findEnviados(
                contexto.ambiente(), contexto.cuit(), contexto.puntoVenta(), tipo));

        if (enviados.isEmpty()) {
            return;
        }

        Wsfe.Auth auth = tickets.obtener(contexto);

        for (Long id : enviados) {

            Enviado enviado = transaccionNueva.execute(s -> {
                ComprobanteArca c = comprobanteRepository.findById(id).orElseThrow();
                return new Enviado(formatear(c), c.getPuntoVenta(), c.getNumero(), c.getFecha(),
                        c.getImporteTotal(), c.getDocNro() == null ? 0 : c.getDocNro());
            });

            Optional<Wsfe.ComprobanteEmitido> emitido;

            try {
                emitido = wsfe.consultar(contexto.ambiente(), auth, enviado.puntoVenta(), tipo.getCodigoArca(),
                        enviado.numero());
            } catch (ArcaComunicacionException e) {
                throw new ArcaComunicacionException("No se pudo verificar en ARCA si se autorizó la " + enviado.comprobante()
                        + ", enviada sin respuesta. Reintente más tarde. Detalle: " + e.getMessage(), e);
            } catch (ArcaException e) {
                throw new ArcaException("No se pudo verificar en ARCA si se autorizó la " + enviado.comprobante()
                        + ", enviada sin respuesta. Detalle: " + e.getMessage(), e);
            }

            transaccionNueva.executeWithoutResult(s -> confirmar(id, enviado, emitido.orElse(null)));
        }
    }

    private void confirmar(Long id, Enviado enviado, Wsfe.ComprobanteEmitido emitido) {

        ComprobanteArca c = comprobanteRepository.findById(id).orElseThrow();

        if (c.getEstado() != EstadoComprobanteArca.ENVIADO) {
            return;
        }

        c.setFechaProceso(LocalDateTime.now());

        boolean autorizado = emitido != null && "A".equals(emitido.resultado()) && emitido.cae() != null;

        if (autorizado && coincide(enviado, emitido)) {
            c.setEstado(EstadoComprobanteArca.AUTORIZADO);
            c.setCae(emitido.cae());
            c.setVencimientoCae(emitido.vencimientoCae());
            c.setObservaciones("CAE confirmado con ARCA después de un envío sin respuesta.");
            log.info("Se confirmó en ARCA {} con CAE {}", enviado.comprobante(), emitido.cae());
            return;
        }

        c.setEstado(EstadoComprobanteArca.PENDIENTE);
        c.setNumero(null);

        if (autorizado) {
            // El número lo usó otro comprobante (por ejemplo, emitido desde otro sistema con el mismo punto de venta).
            c.setObservaciones("El número " + enviado.numero() + " figura en ARCA con otros datos (total "
                    + emitido.importeTotal() + "). Se pedirá un número nuevo. Verifique que el punto de venta "
                    + "no se use desde otro sistema.");
            log.warn("{} figura en ARCA con otros datos: {}", enviado.comprobante(), emitido);
        } else {
            c.setObservaciones("ARCA no había recibido el comprobante; se vuelve a enviar.");
        }
    }

    private static boolean coincide(Enviado enviado, Wsfe.ComprobanteEmitido emitido) {
        return emitido.importeTotal().compareTo(enviado.importeTotal()) == 0
                && emitido.docNro() == enviado.docNro()
                && (emitido.fecha() == null || emitido.fecha().equals(enviado.fecha()));
    }

    private void registrarObservacion(Long id, String observacion) {
        transaccionNueva.executeWithoutResult(s -> comprobanteRepository.findById(id).ifPresent(c -> {
            c.setObservaciones(observacion);
            c.setFechaProceso(LocalDateTime.now());
        }));
    }

    private static String unir(List<Wsfe.Mensaje> observaciones, List<Wsfe.Mensaje> errores) {

        List<Wsfe.Mensaje> todos = new ArrayList<>(observaciones);
        todos.addAll(errores);

        return todos.isEmpty()
                ? null
                : todos.stream().map(Wsfe.Mensaje::toString).collect(Collectors.joining("\n"));
    }

    private static String formatear(ComprobanteArca c) {
        return TipoComprobante.formatear(c.getTipoComprobante(), c.getPuntoVenta(), c.getNumero()).toLowerCase();
    }

}
