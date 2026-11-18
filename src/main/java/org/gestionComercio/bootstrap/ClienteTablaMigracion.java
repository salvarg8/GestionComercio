package org.gestionComercio.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adapta la tabla clientes de bases creadas antes del módulo de clientes.
 *
 * <p>La versión anterior tenía un campo activo propio (además de estado_registro),
 * apellido obligatorio, numero_documento UNIQUE por sí solo y no tenía condición
 * frente al IVA. Con ddl-auto=update Hibernate no quita columnas ni cambia
 * restricciones, y SQLite no permite cambiarlas con ALTER TABLE ni agregar una
 * columna NOT NULL sin valor por defecto: hay que reconstruir la tabla
 * (crear la nueva, copiar los datos, borrar la vieja y renombrar).</p>
 *
 * <p>Se ejecuta una sola vez: si la tabla ya tiene condicion_iva y no tiene
 * activo, no hace nada.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClienteTablaMigracion {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void migrarSiCorresponde() {

        List<String> columnas = jdbcTemplate.queryForList(
                "select name from pragma_table_info('clientes')", String.class);

        if (columnas.isEmpty()
                || (columnas.contains("condicion_iva") && !columnas.contains("activo"))) {
            return;
        }

        log.info("Migrando la tabla clientes al nuevo esquema.");

        jdbcTemplate.execute("drop table if exists clientes_nueva");

        jdbcTemplate.execute("""
                create table clientes_nueva (
                    id integer,
                    estado_registro varchar(255) not null
                        check (estado_registro in ('ACTIVO','INACTIVO')),
                    fecha_creacion timestamp not null,
                    fecha_modificacion timestamp not null,
                    usuario_creacion_id bigint,
                    usuario_modificacion_id bigint,
                    nombre varchar(150) not null,
                    apellido varchar(100),
                    tipo_documento varchar(255) not null
                        check (tipo_documento in ('DNI','CUIT','CUIL','PASAPORTE')),
                    numero_documento varchar(20) not null,
                    condicion_iva varchar(255) not null
                        check (condicion_iva in ('RESPONSABLE_INSCRIPTO','MONOTRIBUTISTA',
                                                 'EXENTO','CONSUMIDOR_FINAL')),
                    direccion varchar(150),
                    telefono varchar(50),
                    email varchar(100),
                    primary key (id),
                    constraint uk_clientes_documento unique (tipo_documento, numero_documento)
                )
                """);

        String estado = columnas.contains("activo")
                ? "case when activo = 0 then 'INACTIVO' else estado_registro end"
                : "estado_registro";

        String condicionIva = columnas.contains("condicion_iva")
                ? "coalesce(condicion_iva, 'CONSUMIDOR_FINAL')"
                : "'CONSUMIDOR_FINAL'";

        // El documento se guarda sin guiones, puntos ni espacios, como lo deja el servicio.
        // Un cliente viejo sin documento queda con un marcador para no perder el registro.
        jdbcTemplate.execute("""
                insert into clientes_nueva (
                    id, estado_registro, fecha_creacion, fecha_modificacion,
                    usuario_creacion_id, usuario_modificacion_id,
                    nombre, apellido, tipo_documento, numero_documento, condicion_iva,
                    direccion, telefono, email)
                select
                    id,
                    %s,
                    fecha_creacion, fecha_modificacion,
                    usuario_creacion_id, usuario_modificacion_id,
                    nombre,
                    nullif(trim(apellido), ''),
                    tipo_documento,
                    coalesce(
                        nullif(replace(replace(replace(numero_documento, '-', ''), '.', ''), ' ', ''), ''),
                        'SIN-DOC-' || id),
                    %s,
                    direccion, telefono, email
                from clientes
                """.formatted(estado, condicionIva));

        jdbcTemplate.execute("drop table clientes");

        jdbcTemplate.execute("alter table clientes_nueva rename to clientes");
    }
}
