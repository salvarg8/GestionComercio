package org.gestionComercio.backup;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(BackupProperties.class)
public class BackupConfig {

    @Bean
    ConexionPostgres conexionPostgres(DataSourceProperties dataSource) {
        return ConexionPostgres.desdeJdbc(
                dataSource.determineUrl(),
                dataSource.determineUsername(),
                dataSource.determinePassword());
    }

    /**
     * Antes de aplicar las migraciones se verifica que nadie esté restaurando:
     * Flyway vería la base a medio restaurar e intentaría crear las tablas.
     */
    @Bean
    FlywayMigrationStrategy flywayMigrationStrategy(BloqueoRestauracion bloqueo) {
        return flyway -> {
            if (bloqueo.restauracionEnCurso()) {
                throw new RestauracionEnCursoException();
            }
            flyway.migrate();
        };
    }
}
