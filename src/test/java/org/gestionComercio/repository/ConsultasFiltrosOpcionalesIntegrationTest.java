package org.gestionComercio.repository;

import org.gestionComercio.config.AuditorProvider;
import org.gestionComercio.config.JpaAuditingConfig;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.support.PostgresTestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Ejecuta contra PostgreSQL las consultas con filtros opcionales
 * ({@code :param is null or ...}), con los filtros vacíos y completos.
 *
 * <p>PostgreSQL rechaza un parámetro null cuyo tipo no puede deducir
 * ("could not determine data type of parameter"); SQLite lo aceptaba. Este
 * test detecta una consulta nueva o modificada que vuelva a caer en eso.</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        PostgresTestcontainersConfig.class,
        SesionUsuario.class,
        JpaAuditingConfig.class,
        AuditorProvider.class
})
class ConsultasFiltrosOpcionalesIntegrationTest {

    private static final LocalDateTime DESDE = LocalDateTime.of(2026, 1, 1, 0, 0);
    private static final LocalDateTime HASTA = LocalDateTime.of(2026, 12, 31, 23, 59);

    @Autowired
    private CajaRepository cajaRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ClienteRepository clienteRepository;

    @Autowired
    private ProveedorRepository proveedorRepository;

    @Test
    @DisplayName("Las consultas aceptan todos los filtros opcionales en null")
    void filtrosVacios() {
        assertThatCode(() -> {
            cajaRepository.findCerradas(null, null);
            ventaRepository.buscar(null, null, null);
            movimientoInventarioRepository.consultar(null, null, null, null);
            productoRepository.buscar("", null);
            productoRepository.consultarStock("", null, "TODOS");
            clienteRepository.buscar("", "", null);
            proveedorRepository.buscar("", "", null);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Las consultas aceptan todos los filtros opcionales completos")
    void filtrosCompletos() {
        assertThatCode(() -> {
            cajaRepository.findCerradas(DESDE, HASTA);
            ventaRepository.buscar(DESDE, HASTA, EstadoVenta.values()[0]);
            movimientoInventarioRepository.consultar(1L, TipoMovimientoInventario.values()[0], DESDE, HASTA);
            productoRepository.buscar("arroz", EstadoRegistro.ACTIVO);
            productoRepository.consultarStock("arroz", 1L, "STOCK_BAJO");
            clienteRepository.buscar("perez", "20", EstadoRegistro.ACTIVO);
            proveedorRepository.buscar("acme", "30", EstadoRegistro.ACTIVO);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Las consultas aceptan sólo una de las fechas")
    void unaSolaFecha() {
        assertThatCode(() -> {
            cajaRepository.findCerradas(DESDE, null);
            cajaRepository.findCerradas(null, HASTA);
            ventaRepository.buscar(DESDE, null, null);
            ventaRepository.buscar(null, HASTA, null);
            movimientoInventarioRepository.consultar(null, null, DESDE, null);
            movimientoInventarioRepository.consultar(null, null, null, HASTA);
        }).doesNotThrowAnyException();
    }
}
