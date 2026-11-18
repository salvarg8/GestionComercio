package org.gestionComercio.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Permite ventas sin cliente (consumidor final) en bases creadas antes del
 * módulo de ventas, donde ventas.cliente_id es NOT NULL.
 *
 * <p>SQLite no permite quitar un NOT NULL con ALTER TABLE y Hibernate
 * (ddl-auto=update) no lo intenta: hay que reconstruir la tabla (crear la
 * nueva, copiar los datos, borrar la vieja y renombrar).</p>
 *
 * <p>Se ejecuta una sola vez: si la tabla no existe o cliente_id ya admite
 * null, no hace nada.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VentaTablaMigracion {

    /** Columnas de la tabla nueva, en el orden en que se crean. */
    private static final List<String> COLUMNAS = List.of(
            "id", "estado_registro", "fecha_creacion", "fecha_modificacion",
            "usuario_creacion_id", "usuario_modificacion_id",
            "fecha", "subtotal", "descuento", "total", "forma_pago",
            "cliente_id", "usuario_id", "caja_id", "estado_venta");

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void migrarSiCorresponde() {

        Integer clienteNotNull = jdbcTemplate.query(
                "select \"notnull\" from pragma_table_info('ventas') where name = 'cliente_id'",
                rs -> rs.next() ? rs.getInt(1) : null);

        if (clienteNotNull == null || clienteNotNull == 0) {
            return;
        }

        log.info("Migrando la tabla ventas: cliente_id pasa a admitir null (consumidor final).");

        List<String> existentes = jdbcTemplate.queryForList(
                "select name from pragma_table_info('ventas')", String.class);

        jdbcTemplate.execute("drop table if exists ventas_nueva");

        jdbcTemplate.execute("""
                create table ventas_nueva (
                    id integer,
                    estado_registro varchar(255) not null
                        check (estado_registro in ('ACTIVO','INACTIVO')),
                    fecha_creacion timestamp not null,
                    fecha_modificacion timestamp not null,
                    usuario_creacion_id bigint,
                    usuario_modificacion_id bigint,
                    fecha timestamp not null,
                    subtotal numeric(12,2) not null,
                    descuento numeric(12,2) not null,
                    total numeric(12,2) not null,
                    forma_pago varchar(255) not null
                        check (forma_pago in ('EFECTIVO','TARJETA_DEBITO','TARJETA_CREDITO',
                                              'TRANSFERENCIA','MERCADO_PAGO','CUENTA_CORRIENTE')),
                    cliente_id bigint,
                    usuario_id bigint not null,
                    caja_id bigint not null,
                    estado_venta varchar(255) not null
                        check (estado_venta in ('PENDIENTE','PAGADA','CANCELADA')),
                    primary key (id)
                )
                """);

        // Sólo se copian las columnas que ya existían; las demás no pueden tener datos.
        String columnas = COLUMNAS.stream()
                .filter(existentes::contains)
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("insert into ventas_nueva (" + columnas + ") select " + columnas + " from ventas");

        jdbcTemplate.execute("drop table ventas");

        jdbcTemplate.execute("alter table ventas_nueva rename to ventas");
    }
}
