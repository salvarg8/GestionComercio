package org.gestionComercio.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adapta la tabla proveedores de bases creadas antes del módulo de proveedores.
 *
 * <p>La versión anterior tenía tipo_documento y numero_documento obligatorios,
 * contacto y un campo activo propio. Con ddl-auto=update Hibernate agrega
 * columnas nuevas pero nunca quita columnas ni restricciones NOT NULL, y SQLite
 * no permite cambiarlas con ALTER TABLE: hay que reconstruir la tabla
 * (crear la nueva, copiar los datos, borrar la vieja y renombrar).</p>
 *
 * <p>Se ejecuta una sola vez: si la tabla ya no tiene numero_documento, no hace nada.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProveedorTablaMigracion {

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void migrarSiCorresponde() {

        List<String> columnas = jdbcTemplate.queryForList(
                "select name from pragma_table_info('proveedores')", String.class);

        if (!columnas.contains("numero_documento")) {
            return;
        }

        log.info("Migrando la tabla proveedores al nuevo esquema.");

        jdbcTemplate.execute("drop table if exists proveedores_nueva");

        jdbcTemplate.execute("""
                create table proveedores_nueva (
                    id integer,
                    estado_registro varchar(255) not null
                        check (estado_registro in ('ACTIVO','INACTIVO')),
                    fecha_creacion timestamp not null,
                    fecha_modificacion timestamp not null,
                    usuario_creacion_id bigint,
                    usuario_modificacion_id bigint,
                    razon_social varchar(150) not null,
                    cuit varchar(11) unique,
                    telefono varchar(50),
                    email varchar(100),
                    direccion varchar(150),
                    observaciones varchar(500),
                    primary key (id)
                )
                """);

        // Sólo se conserva como CUIT el documento que era de tipo CUIT; el contacto
        // y los otros documentos pasan a observaciones para no perder el dato.
        jdbcTemplate.execute("""
                insert into proveedores_nueva (
                    id, estado_registro, fecha_creacion, fecha_modificacion,
                    usuario_creacion_id, usuario_modificacion_id,
                    razon_social, cuit, telefono, email, direccion, observaciones)
                select
                    id,
                    case when activo then estado_registro else 'INACTIVO' end,
                    fecha_creacion, fecha_modificacion,
                    usuario_creacion_id, usuario_modificacion_id,
                    razon_social,
                    case when tipo_documento = 'CUIT'
                         then replace(replace(replace(numero_documento, '-', ''), '.', ''), ' ', '')
                    end,
                    telefono, email, direccion,
                    nullif(trim(
                        coalesce('Contacto: ' || contacto, '')
                        || case when tipo_documento <> 'CUIT'
                                then ' ' || tipo_documento || ': ' || numero_documento
                                else '' end
                    ), '')
                from proveedores
                """);

        jdbcTemplate.execute("drop table proveedores");

        jdbcTemplate.execute("alter table proveedores_nueva rename to proveedores");
    }
}
