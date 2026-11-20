package org.gestionComercio.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Levanta un PostgreSQL real en Docker para los tests de integración y apunta
 * el DataSource a él ({@link ServiceConnection} reemplaza URL, usuario y
 * contraseña de application.properties). Usa la misma versión mayor que el
 * docker-compose.yml de desarrollo.
 *
 * <p>Requiere Docker en ejecución.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainersConfig {

    public static final DockerImageName IMAGEN = DockerImageName.parse("postgres:17-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(IMAGEN);
    }
}
