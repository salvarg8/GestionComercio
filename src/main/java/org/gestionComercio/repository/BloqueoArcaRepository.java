package org.gestionComercio.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueos de PostgreSQL (advisory locks de transacción) que coordinan a
 * todas las PC del comercio frente a ARCA. Se liberan solos al terminar la
 * transacción, aunque la aplicación se cierre o se corte la conexión.
 */
@Repository
public class BloqueoArcaRepository {

    /** Primera clave de los bloqueos de ARCA (las de otros módulos usan otra). */
    private static final int ESPACIO_ARCA = 361;

    /** Segunda clave del bloqueo del ticket de acceso. */
    private static final int TICKET_ACCESO = -1;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Bloquea la numeración de un punto de venta y tipo de comprobante: otra PC
     * que quiera emitir el mismo tipo en el mismo punto de venta espera a que
     * termine esta transacción. Así nunca dos cajas piden el mismo número.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bloquearNumeracion(int puntoVenta, int tipoComprobanteArca) {
        bloquear(puntoVenta * 1000 + tipoComprobanteArca);
    }

    /** Bloquea el pedido de ticket de acceso: ARCA no entrega dos tickets vigentes a la vez. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bloquearTicketAcceso() {
        bloquear(TICKET_ACCESO);
    }

    private void bloquear(int clave) {
        // pg_advisory_xact_lock devuelve void, que Hibernate no sabe leer: se cuenta la fila.
        entityManager.createNativeQuery(
                        "select count(*) from (select pg_advisory_xact_lock(:espacio, :clave)) bloqueo")
                .setParameter("espacio", ESPACIO_ARCA)
                .setParameter("clave", clave)
                .getSingleResult();
    }

}
