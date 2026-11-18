package org.gestionComercio.service.impl;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import org.gestionComercio.config.AuditorProvider;
import org.gestionComercio.config.JpaAuditingConfig;
import org.gestionComercio.dto.inventario.LineaInventario;
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.MotivoAjusteInventario;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ConcurrencyException;
import org.gestionComercio.mapper.MovimientoInventarioMapperImpl;
import org.gestionComercio.repository.MovimientoInventarioRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.InventarioComprobanteService;
import org.gestionComercio.service.InventarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba los servicios de inventario contra una base SQLite real (la misma
 * tecnología y la misma configuración de conexión que usa la aplicación) para
 * verificar el control de {@code @Version}, los decimales, la reversión de
 * comprobantes y que el movimiento y el stock se confirmen o reviertan juntos.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/inventario-it.db",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        InventarioServiceImpl.class,
        InventarioComprobanteServiceImpl.class,
        RegistradorMovimientosInventario.class,
        MovimientoInventarioMapperImpl.class,
        SesionUsuario.class,
        JpaAuditingConfig.class,
        AuditorProvider.class
})
class InventarioServiceIntegrationTest {

    private static final AtomicLong SECUENCIA = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private InventarioComprobanteService comprobanteService;

    @Autowired
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Autowired
    private SesionUsuario sesionUsuario;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private TransactionTemplate transaccion;

    private String sufijo;

    private Categoria categoria;
    private Marca marca;
    private Proveedor proveedor;

    @BeforeEach
    void setUp() {
        transaccion = new TransactionTemplate(transactionManager);
        sesionUsuario.cerrarSesion();

        sufijo = String.valueOf(SECUENCIA.incrementAndGet());

        Usuario usuario = transaccion.execute(status -> {
            Empresa empresa = Empresa.builder()
                    .razonSocial("Empresa " + sufijo).cuit("30" + sufijo)
                    .condicionIva(CondicionIVA.RESPONSABLE_INSCRIPTO)
                    .build();
            Rol rol = Rol.builder().nombre("ROL" + sufijo).build();
            Usuario u = Usuario.builder()
                    .usuario("user" + sufijo).password("x").nombre("Ana").apellido("Pérez")
                    .empresa(empresa).rol(rol).tipoDocumento(TipoDocumento.DNI).documento(sufijo)
                    .build();
            categoria = Categoria.builder().nombre("Categoria " + sufijo).build();
            marca = Marca.builder().nombre("Marca " + sufijo).build();
            proveedor = Proveedor.builder().razonSocial("Proveedor " + sufijo).build();
            entityManager.persist(empresa);
            entityManager.persist(rol);
            entityManager.persist(u);
            entityManager.persist(categoria);
            entityManager.persist(marca);
            entityManager.persist(proveedor);
            return u;
        });

        sesionUsuario.iniciarSesion(usuario, EnumSet.allOf(PermisoCodigo.class));
    }

    private Long crearProducto(String nombre, UnidadMedida unidad) {
        return transaccion.execute(status -> {
            Producto producto = Producto.builder()
                    .codigo(nombre + sufijo).nombre(nombre + " " + sufijo).unidadMedida(unidad)
                    .categoria(entityManager.merge(categoria))
                    .marca(entityManager.merge(marca))
                    .proveedor(entityManager.merge(proveedor))
                    .build();
            entityManager.persist(producto);
            return producto.getId();
        });
    }

    private Producto productoEnBase(Long id) {
        return transaccion.execute(status -> entityManager.find(Producto.class, id));
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    // ------------------------------------------------------------------
    // Configuración de la conexión (application.properties)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La conexión usa una sola conexión y espera si la base está bloqueada")
    void configuracionDeLaConexion() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(1);

        Integer busyTimeout = new JdbcTemplate(dataSource).queryForObject("pragma busy_timeout", Integer.class);
        assertThat(busyTimeout).isEqualTo(5000);
    }

    // ------------------------------------------------------------------
    // Operaciones manuales
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Cada movimiento persiste, actualiza el stock e incrementa la versión del producto")
    void movimientosActualizanStockYVersion() {
        Long id = crearProducto("Yerba", UnidadMedida.UNIDAD);
        long versionInicial = productoEnBase(id).getVersion();

        inventarioService.registrarEntrada(id, bd("10"), "Compra");
        inventarioService.registrarSalida(id, bd("3"), "Venta");

        Producto producto = productoEnBase(id);
        assertThat(producto.getStock()).isEqualByComparingTo("7");
        assertThat(producto.getVersion()).isEqualTo(versionInicial + 2);
        assertThat(movimientoInventarioRepository.countByProductoId(id)).isEqualTo(2);
        assertThat(movimientoInventarioRepository.findFirstByProductoIdOrderByFechaDescIdDesc(id))
                .hasValueSatisfying(m -> assertThat(m.getStockResultante()).isEqualByComparingTo("7"));
    }

    @Test
    @DisplayName("Las cantidades con decimales se guardan y se leen sin perder precisión")
    void decimalesPersistidos() {
        Long id = crearProducto("Queso", UnidadMedida.KILOGRAMO);

        inventarioService.registrarEntrada(id, bd("5.125"), "Compra");
        inventarioService.registrarSalida(id, bd("0.750"), "Venta");
        inventarioService.registrarSalida(id, bd("0.1"), "Venta");

        assertThat(productoEnBase(id).getStock()).isEqualByComparingTo("4.275");

        List<MovimientoInventarioDto> historial = inventarioService.historial(id);
        assertThat(historial).extracting(MovimientoInventarioDto::getCantidad)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("-0.1"), bd("-0.75"), bd("5.125"));
    }

    @Test
    @DisplayName("ajustarStock() registra la diferencia con lo contado y guarda la causa")
    void ajustePorRecuento() {
        Long id = crearProducto("Fideos", UnidadMedida.UNIDAD);
        inventarioService.registrarEntrada(id, bd("10"), "Compra");

        MovimientoInventarioDto ajuste = inventarioService.ajustarStock(
                id, bd("8"), MotivoAjusteInventario.RECUENTO, "Recuento de fin de mes");

        assertThat(ajuste.getCantidad()).isEqualByComparingTo("-2");
        assertThat(ajuste.getMotivoAjuste()).isEqualTo(MotivoAjusteInventario.RECUENTO);
        assertThat(productoEnBase(id).getStock()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("ajustarStock() guarda un movimiento AJUSTE con el stock anterior y el resultante")
    void ajustePorRecuentoRegistraStocks() {
        Long id = crearProducto("Yerba", UnidadMedida.UNIDAD);
        inventarioService.registrarEntrada(id, bd("5"), "Compra");

        MovimientoInventarioDto ajuste = inventarioService.ajustarStock(
                id, bd("9"), MotivoAjusteInventario.OTRO, "Mercadería sin registrar");

        MovimientoInventarioDto guardado = inventarioService.historial(id).get(0);

        assertThat(guardado.getId()).isEqualTo(ajuste.getId());
        assertThat(guardado.getTipo()).isEqualTo(TipoMovimientoInventario.AJUSTE);
        assertThat(guardado.getStockAnterior()).isEqualByComparingTo("5");
        assertThat(guardado.getCantidad()).isEqualByComparingTo("4");
        assertThat(guardado.getStockResultante()).isEqualByComparingTo("9");
        assertThat(guardado.getMotivo()).isEqualTo("Mercadería sin registrar");
        assertThat(productoEnBase(id).getStock()).isEqualByComparingTo("9");
    }

    @Test
    @DisplayName("Si otra operación modificó el producto, falla con ConcurrencyException y no guarda nada")
    void conflictoDeVersion() {
        Long id = crearProducto("Arroz", UnidadMedida.UNIDAD);
        inventarioService.registrarEntrada(id, bd("10"), "Stock inicial");

        // La transacción externa queda marcada para rollback: aunque quien llama
        // atrape la ConcurrencyException, no puede confirmar nada de lo hecho.
        assertThatThrownBy(() -> transaccion.executeWithoutResult(status -> {

            // Esta transacción lee el producto (stock 10, versión N)...
            Producto leido = entityManager.find(Producto.class, id);
            assertThat(leido.getStock()).isEqualByComparingTo("10");

            // ...y otra operación lo modifica antes de que se guarde.
            entityManager.createNativeQuery(
                            "update productos set stock = stock - 4, version = version + 1 where id = ?")
                    .setParameter(1, id)
                    .executeUpdate();

            assertThatThrownBy(() -> inventarioService.registrarSalida(id, bd("8"), "Venta"))
                    .isInstanceOf(ConcurrencyException.class);

            assertThat(status.isRollbackOnly()).isTrue();
        })).isInstanceOf(UnexpectedRollbackException.class);

        // Se revirtió la transacción completa: sólo queda el movimiento inicial.
        assertThat(productoEnBase(id).getStock()).isEqualByComparingTo("10");
        assertThat(movimientoInventarioRepository.countByProductoId(id)).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // Comprobantes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una venta descuenta el stock y su anulación lo restituye exactamente")
    void ventaYAnulacion() {
        Long yerba = crearProducto("Yerba", UnidadMedida.UNIDAD);
        Long queso = crearProducto("Queso", UnidadMedida.KILOGRAMO);
        inventarioService.registrarEntrada(yerba, bd("10"), "Compra");
        inventarioService.registrarEntrada(queso, bd("3"), "Compra");
        long ventaId = SECUENCIA.incrementAndGet();

        List<MovimientoInventarioDto> salidas = comprobanteService.registrarSalidas(
                OrigenMovimientoInventario.VENTA, ventaId,
                List.of(new LineaInventario(yerba, bd("2")),
                        new LineaInventario(queso, bd("0.5")),
                        new LineaInventario(yerba, bd("1"))),
                "Venta N° " + ventaId);

        assertThat(salidas).hasSize(2);
        assertThat(productoEnBase(yerba).getStock()).isEqualByComparingTo("7");
        assertThat(productoEnBase(queso).getStock()).isEqualByComparingTo("2.5");

        assertThatThrownBy(() -> comprobanteService.registrarSalidas(
                OrigenMovimientoInventario.VENTA, ventaId, List.of(new LineaInventario(yerba, bd("1"))), "Otra vez"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya registró");

        List<MovimientoInventarioDto> reversiones = comprobanteService.revertir(
                OrigenMovimientoInventario.VENTA, ventaId, "Anulación de la venta N° " + ventaId);

        assertThat(reversiones).hasSize(2)
                .allSatisfy(m -> {
                    assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.ENTRADA);
                    assertThat(m.getMovimientoRevertidoId()).isNotNull();
                    assertThat(m.getOrigenId()).isEqualTo(ventaId);
                });
        assertThat(productoEnBase(yerba).getStock()).isEqualByComparingTo("10");
        assertThat(productoEnBase(queso).getStock()).isEqualByComparingTo("3");

        assertThatThrownBy(() -> comprobanteService.revertir(OrigenMovimientoInventario.VENTA, ventaId, "Otra vez"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya fueron revertidos");
    }

    @Test
    @DisplayName("Si un renglón de la venta no tiene stock, no se descuenta ninguno")
    void ventaSinStockNoDescuentaNada() {
        Long yerba = crearProducto("Yerba", UnidadMedida.UNIDAD);
        Long azucar = crearProducto("Azucar", UnidadMedida.UNIDAD);
        inventarioService.registrarEntrada(yerba, bd("10"), "Compra");
        long ventaId = SECUENCIA.incrementAndGet();

        assertThatThrownBy(() -> comprobanteService.registrarSalidas(
                OrigenMovimientoInventario.VENTA, ventaId,
                List.of(new LineaInventario(yerba, bd("2")), new LineaInventario(azucar, bd("1"))),
                "Venta"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuficiente");

        assertThat(productoEnBase(yerba).getStock()).isEqualByComparingTo("10");
        assertThat(movimientoInventarioRepository.existsByOrigenAndOrigenIdAndMovimientoRevertidoIsNull(
                OrigenMovimientoInventario.VENTA, ventaId)).isFalse();
    }

    @Test
    @DisplayName("Si falla la operación que lo contiene, se revierten el movimiento y el stock")
    void rollbackDeLaTransaccionExterna() {
        Long id = crearProducto("Harina", UnidadMedida.UNIDAD);
        long compraId = SECUENCIA.incrementAndGet();

        assertThatThrownBy(() -> transaccion.executeWithoutResult(status -> {
            comprobanteService.registrarEntradas(OrigenMovimientoInventario.COMPRA, compraId,
                    List.of(new LineaInventario(id, bd("5"))), "Compra");
            throw new IllegalStateException("Falla el resto de la compra");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(productoEnBase(id).getStock()).isZero();
        assertThat(movimientoInventarioRepository.countByProductoId(id)).isZero();
    }

}
