package org.gestionComercio.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.gestionComercio.enums.TipoComprobante;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Numeración correlativa de los comprobantes, por tipo (tabla
 * {@code numeradores_comprobante}).
 */
@Repository
public class NumeradorComprobanteRepository {

    /**
     * Incrementa el último número del tipo en una sola sentencia (o crea el
     * numerador en 1 si el tipo todavía no tiene). La fila queda bloqueada
     * hasta el fin de la transacción: otra venta del mismo tipo espera, y si
     * la transacción se revierte el número vuelve a quedar libre.
     */
    private static final String SIGUIENTE = """
            insert into numeradores_comprobante (tipo_comprobante, ultimo_numero)
            values (:tipo, 1)
            on conflict (tipo_comprobante)
              do update set ultimo_numero = numeradores_comprobante.ultimo_numero + 1
            returning ultimo_numero
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Reserva el próximo número del tipo. Debe llamarse dentro de la
     * transacción que guarda el comprobante.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long siguiente(TipoComprobante tipo) {

        Object numero = entityManager.createNativeQuery(SIGUIENTE)
                .setParameter("tipo", tipo.name())
                .getSingleResult();

        return ((Number) numero).longValue();
    }

}
