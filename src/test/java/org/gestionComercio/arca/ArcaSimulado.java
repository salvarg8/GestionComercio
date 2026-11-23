package org.gestionComercio.arca;

import org.bouncycastle.cms.CMSSignedData;
import org.gestionComercio.enums.AmbienteArca;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WSAA y WSFEv1 en memoria para los tests. Como ARCA, sólo autoriza el
 * número siguiente al último autorizado de cada punto de venta y tipo, y
 * guarda lo autorizado para FECompConsultar. Permite simular rechazos y
 * cortes de conexión.
 */
public class ArcaSimulado implements WsaaCliente, WsfeCliente {

    /** Cómo responde el próximo FECAESolicitar. */
    public enum Proximo {
        NORMAL,
        /** Lo rechaza con el error 10242 (falta la condición frente al IVA). */
        RECHAZAR,
        /** Lo autoriza pero la respuesta no llega (corte después de enviar). */
        CORTAR_DESPUES_DE_AUTORIZAR,
        /** El pedido no llega a ARCA. */
        CORTAR_ANTES_DE_RECIBIR
    }

    private final Object candado = new Object();
    private final Map<String, Long> ultimos = new HashMap<>();
    private final Map<String, Wsfe.ComprobanteEmitido> emitidos = new HashMap<>();
    private final List<Wsfe.SolicitudCae> solicitudes = new ArrayList<>();
    private final AtomicInteger logins = new AtomicInteger();
    private final AtomicInteger rechazosPorNumero = new AtomicInteger();
    private final AtomicLong cae = new AtomicLong(70_000_000_000_000L);

    private volatile Proximo proximo = Proximo.NORMAL;
    private volatile boolean sinConexion;
    private volatile boolean puntoVentaInvalido;

    public void proximo(Proximo comportamiento) {
        this.proximo = comportamiento;
    }

    public void sinConexion(boolean valor) {
        this.sinConexion = valor;
    }

    /** FECompUltimoAutorizado responde con error, como con un punto de venta no habilitado para web services. */
    public void puntoVentaInvalido(boolean valor) {
        this.puntoVentaInvalido = valor;
    }

    public int logins() {
        return logins.get();
    }

    /** FECAESolicitar rechazados por número fuera de secuencia (no debería haber ninguno). */
    public int rechazosPorNumero() {
        return rechazosPorNumero.get();
    }

    public List<Wsfe.SolicitudCae> solicitudes() {
        synchronized (candado) {
            return List.copyOf(solicitudes);
        }
    }

    public long ultimo(int puntoVenta, int tipo) {
        synchronized (candado) {
            return ultimos.getOrDefault(clave(puntoVenta, tipo), 0L);
        }
    }

    // ------------------------------------------------------------------
    // WSAA
    // ------------------------------------------------------------------

    @Override
    public TicketAcceso login(AmbienteArca ambiente, String traFirmado) {
        verificarConexion();

        try {
            CMSSignedData cms = new CMSSignedData(Base64.getDecoder().decode(traFirmado));
            String tra = new String((byte[]) cms.getSignedContent().getContent(), StandardCharsets.UTF_8);

            if (!tra.contains("<service>wsfe</service>")) {
                throw new ArcaException("TRA sin el servicio wsfe");
            }
        } catch (ArcaException e) {
            throw e;
        } catch (Exception e) {
            throw new ArcaException("CMS inválido: " + e.getMessage());
        }

        logins.incrementAndGet();
        Instant ahora = Instant.now();
        return new TicketAcceso("token-" + logins.get(), "sign", ahora, ahora.plus(Duration.ofHours(12)));
    }

    // ------------------------------------------------------------------
    // WSFEv1
    // ------------------------------------------------------------------

    @Override
    public Wsfe.EstadoServidores estado(AmbienteArca ambiente) {
        verificarConexion();
        return new Wsfe.EstadoServidores("OK", "OK", "OK");
    }

    @Override
    public long ultimoAutorizado(AmbienteArca ambiente, Wsfe.Auth auth, int puntoVenta, int tipoComprobante) {
        verificarConexion();
        if (puntoVentaInvalido) {
            throw new ArcaException("ARCA no informó el último comprobante autorizado del punto de venta "
                    + puntoVenta + ": 11002 - El punto de venta no se encuentra habilitado a usar el presente WS");
        }
        long ultimo = ultimo(puntoVenta, tipoComprobante);
        demorar(); // agranda la ventana en la que dos cajas sin bloqueo pedirían el mismo número
        return ultimo;
    }

    @Override
    public Wsfe.RespuestaCae solicitarCae(AmbienteArca ambiente, Wsfe.Auth auth, Wsfe.SolicitudCae s) {

        verificarConexion();
        Proximo comportamiento = proximo;
        proximo = Proximo.NORMAL;

        if (comportamiento == Proximo.CORTAR_ANTES_DE_RECIBIR) {
            throw new ArcaComunicacionException("Tiempo agotado (simulado)");
        }

        synchronized (candado) {

            solicitudes.add(s);

            if (comportamiento == Proximo.RECHAZAR) {
                return new Wsfe.RespuestaCae("R", null, null,
                        List.of(new Wsfe.Mensaje(10242, "El campo Condicion Frente al IVA del receptor es obligatorio")),
                        List.of());
            }

            String clave = clave(s.puntoVenta(), s.tipoComprobante());
            long ultimo = ultimos.getOrDefault(clave, 0L);

            if (s.numero() != ultimo + 1) {
                rechazosPorNumero.incrementAndGet();
                return new Wsfe.RespuestaCae("R", null, null, List.of(new Wsfe.Mensaje(10016,
                        "El numero o fecha del comprobante no se corresponde con el proximo a autorizar")), List.of());
            }

            String codigo = String.valueOf(cae.incrementAndGet());
            ultimos.put(clave, s.numero());
            emitidos.put(clave + "-" + s.numero(), new Wsfe.ComprobanteEmitido(s.puntoVenta(), s.tipoComprobante(),
                    s.numero(), s.fecha(), s.docTipo(), s.docNro(), s.importeTotal(), "A", codigo,
                    s.fecha().plusDays(10)));

            if (comportamiento == Proximo.CORTAR_DESPUES_DE_AUTORIZAR) {
                throw new ArcaComunicacionException("Se cortó la conexión (simulado)");
            }

            return new Wsfe.RespuestaCae("A", codigo, s.fecha().plusDays(10), List.of(), List.of());
        }
    }

    @Override
    public Optional<Wsfe.ComprobanteEmitido> consultar(AmbienteArca ambiente, Wsfe.Auth auth,
                                                       int puntoVenta, int tipoComprobante, long numero) {
        verificarConexion();
        synchronized (candado) {
            return Optional.ofNullable(emitidos.get(clave(puntoVenta, tipoComprobante) + "-" + numero));
        }
    }

    private void verificarConexion() {
        if (sinConexion) {
            throw new ArcaComunicacionException("No se pudo conectar con ARCA (simulado)");
        }
    }

    private static String clave(int puntoVenta, int tipo) {
        return puntoVenta + "/" + tipo;
    }

    private static void demorar() {
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
