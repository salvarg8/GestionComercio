package org.gestionComercio.service.impl.arca;

import lombok.extern.slf4j.Slf4j;
import org.gestionComercio.arca.AutenticacionWsaa;
import org.gestionComercio.arca.TicketAcceso;
import org.gestionComercio.arca.WsaaCliente;
import org.gestionComercio.arca.Wsfe;
import org.gestionComercio.entity.TicketAccesoArca;
import org.gestionComercio.repository.BloqueoArcaRepository;
import org.gestionComercio.repository.TicketAccesoArcaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

/**
 * Ticket de acceso del WSAA guardado en la base y compartido por todas las
 * PC: se reutiliza hasta que vence (unas 12 horas) y sólo entonces se pide
 * otro. El pedido se hace con un bloqueo de la base para que dos PC no lo
 * pidan a la vez (ARCA rechaza el segundo mientras el primero esté vigente).
 *
 * <p>Cada operación corre en su propia transacción, aunque la llame otra que
 * ya esté en curso.
 */
@Slf4j
@Component
public class TicketAccesoService {

    private final TicketAccesoArcaRepository repository;
    private final BloqueoArcaRepository bloqueo;
    private final WsaaCliente wsaa;
    private final TransactionTemplate transaccionNueva;

    public TicketAccesoService(TicketAccesoArcaRepository repository, BloqueoArcaRepository bloqueo,
                               WsaaCliente wsaa, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.bloqueo = bloqueo;
        this.wsaa = wsaa;
        this.transaccionNueva = new TransactionTemplate(transactionManager);
        this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Token y sign vigentes para el WSFEv1, pidiendo un ticket nuevo si hace falta. */
    public Wsfe.Auth obtener(ContextoArca contexto) {

        Optional<TicketAcceso> guardado = transaccionNueva.execute(s -> leer(contexto));

        if (guardado.isPresent() && guardado.get().vigente(Instant.now())) {
            return auth(guardado.get(), contexto);
        }

        return transaccionNueva.execute(s -> {

            bloqueo.bloquearTicketAcceso();

            // Mientras se esperaba el bloqueo, otra PC pudo haberlo renovado.
            Optional<TicketAcceso> actual = leer(contexto);

            if (actual.isPresent() && actual.get().vigente(Instant.now())) {
                return auth(actual.get(), contexto);
            }

            String tra = AutenticacionWsaa.crearTra(AutenticacionWsaa.SERVICIO_WSFE, Instant.now());
            TicketAcceso nuevo = wsaa.login(contexto.ambiente(), AutenticacionWsaa.firmar(tra, contexto.credencial()));

            TicketAccesoArca entidad = repository
                    .findByAmbienteAndCuitAndServicio(contexto.ambiente(), contexto.cuit(),
                            AutenticacionWsaa.SERVICIO_WSFE)
                    .orElseGet(() -> TicketAccesoArca.builder()
                            .ambiente(contexto.ambiente())
                            .cuit(contexto.cuit())
                            .servicio(AutenticacionWsaa.SERVICIO_WSFE)
                            .build());

            entidad.setToken(nuevo.token());
            entidad.setSign(nuevo.sign());
            entidad.setGenerado(nuevo.generado());
            entidad.setVence(nuevo.vence());
            repository.save(entidad);

            log.info("Nuevo ticket de acceso de ARCA ({}), vence {}", contexto.ambiente(), nuevo.vence());

            return auth(nuevo, contexto);
        });
    }

    /** Descarta el ticket guardado (ARCA lo rechazó): el próximo pedido obtiene uno nuevo. */
    public void invalidar(ContextoArca contexto) {
        transaccionNueva.executeWithoutResult(s -> repository
                .findByAmbienteAndCuitAndServicio(contexto.ambiente(), contexto.cuit(), AutenticacionWsaa.SERVICIO_WSFE)
                .ifPresent(repository::delete));
    }

    private Optional<TicketAcceso> leer(ContextoArca contexto) {
        return repository
                .findByAmbienteAndCuitAndServicio(contexto.ambiente(), contexto.cuit(), AutenticacionWsaa.SERVICIO_WSFE)
                .map(t -> new TicketAcceso(t.getToken(), t.getSign(), t.getGenerado(), t.getVence()));
    }

    private static Wsfe.Auth auth(TicketAcceso ticket, ContextoArca contexto) {
        return new Wsfe.Auth(ticket.token(), ticket.sign(), contexto.cuitNumero());
    }

}
