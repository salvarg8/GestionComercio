package org.gestionComercio.service.impl;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import org.gestionComercio.config.AuditorProvider;
import org.gestionComercio.config.JpaAuditingConfig;
import org.gestionComercio.dto.inventario.FiltroMovimientos;
import org.gestionComercio.dto.inventario.LineaInventario;
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.dto.inventario.StockProductoDto;
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
import org.gestionComercio.service.ConsultaStockService;
import org.gestionComercio.service.InventarioComprobanteService;
import org.gestionComercio.service.InventarioService;
import org.gestionComercio.support.PostgresTestcontainersConfig;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba los servicios de inventario contra un PostgreSQL real en Docker (la
 * misma base y la misma configuración de conexión que usa la aplicación) para
 * verificar el control de {@code @Version} entre conexiones simultáneas, los
 * decimales, la reversión de comprobantes y que el movimiento y el stock se
 * confirmen o reviertan juntos.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        PostgresTestcontainersConfig.class,
        InventarioServiceImpl.class,
        ConsultaStockServiceImpl.class,
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
    private ConsultaStockService consultaStockService;

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
    @DisplayName("La aplicación trabaja sobre PostgreSQL con un pool de varias conexiones")
    void configuracionDeLaConexion() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize()).isEqualTo(5);

        String version = new JdbcTemplate(dataSource).queryForObject("select version()", String.class);
        assertThat(version).startsWith("PostgreSQL");
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

    @Test
    @DisplayName("Con dos conexiones simultáneas, la que guarda sobre datos viejos falla y no pisa a la otra")
    void conflictoEntreDosConexiones() {
        Long id = crearProducto("Fideos", UnidadMedida.UNIDAD);
        inventarioService.registrarEntrada(id, bd("10"), "Stock inicial");

        // Otro hilo = otra transacción con su propia conexión, como otra PC.
        ExecutorService otraCaja = Executors.newSingleThreadExecutor();

        try {
            assertThatThrownBy(() -> transaccion.executeWithoutResult(status -> {

                // La conexión 1 lee el producto (stock 10)...
                Producto leido = entityManager.find(Producto.class, id);
                assertThat(leido.getStock()).isEqualByComparingTo("10");

                // ...la conexión 2 vende 4 y confirma...
                esperar(otraCaja.submit(() -> inventarioService.registrarSalida(id, bd("4"), "Otra caja")));

                // ...y la 1 intenta guardar a partir del stock que había leído.
                assertThatThrownBy(() -> inventarioService.registrarSalida(id, bd("8"), "Venta"))
                        .isInstanceOf(ConcurrencyException.class);

            })).isInstanceOf(UnexpectedRollbackException.class);
        } finally {
            otraCaja.shutdownNow();
        }

        // Queda sólo lo confirmado por la conexión 2: 10 - 4.
        assertThat(productoEnBase(id).getStock()).isEqualByComparingTo("6");
        assertThat(movimientoInventarioRepository.countByProductoId(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("Varias conexiones registrando movimientos a la vez nunca pierden ni duplican stock")
    void operacionesParalelasNoCorrompenElStock() throws Exception {
        Long id = crearProducto("Azucar", UnidadMedida.UNIDAD);

        int hilos = 4;
        int operacionesPorHilo = 10;

        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger confirmadas = new AtomicInteger();
        AtomicInteger rechazadas = new AtomicInteger();
        List<Future<?>> tareas = new ArrayList<>();

        try {
            for (int h = 0; h < hilos; h++) {
                tareas.add(pool.submit(() -> {
                    largada.await();
                    for (int i = 0; i < operacionesPorHilo; i++) {
                        try {
                            inventarioService.registrarEntrada(id, bd("1"), "Entrada en paralelo");
                            confirmadas.incrementAndGet();
                        } catch (ConcurrencyException e) {
                            rechazadas.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }

            largada.countDown();

            for (Future<?> tarea : tareas) {
                tarea.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // Cada operación se confirmó entera o se rechazó entera: el stock es
        // exactamente la cantidad de entradas confirmadas, una por movimiento.
        assertThat(confirmadas.get() + rechazadas.get()).isEqualTo(hilos * operacionesPorHilo);
        assertThat(confirmadas.get()).isPositive();
        assertThat(productoEnBase(id).getStock()).isEqualByComparingTo(String.valueOf(confirmadas.get()));
        assertThat(movimientoInventarioRepository.countByProductoId(id)).isEqualTo(confirmadas.get());
    }

    private static <T> T esperar(Future<T> futuro) {
        try {
            return futuro.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------
    // Historial de movimientos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("El historial de un producto en orden cronológico reconstruye la evolución de su stock")
    void historialReconstruyeEvolucion() {
        Long id = crearProducto("Yerba", UnidadMedida.UNIDAD);
        inventarioService.registrarEntrada(id, bd("10"), "Compra");
        inventarioService.registrarSalida(id, bd("3"), "Venta");
        inventarioService.ajustarStock(id, bd("9"), MotivoAjusteInventario.RECUENTO, null);
        inventarioService.registrarEntrada(id, bd("2"), "Compra");

        List<MovimientoInventarioDto> movimientos = new ArrayList<>(
                inventarioService.consultarMovimientos(FiltroMovimientos.deProducto(id)));
        Collections.reverse(movimientos);

        assertThat(movimientos).extracting(MovimientoInventarioDto::getTipo).containsExactly(
                TipoMovimientoInventario.ENTRADA, TipoMovimientoInventario.SALIDA,
                TipoMovimientoInventario.AJUSTE, TipoMovimientoInventario.ENTRADA);

        // Cada movimiento parte del stock en que terminó el anterior.
        assertThat(movimientos.get(0).getStockAnterior()).isEqualByComparingTo("0");
        for (int i = 1; i < movimientos.size(); i++) {
            assertThat(movimientos.get(i).getStockAnterior())
                    .isEqualByComparingTo(movimientos.get(i - 1).getStockResultante());
        }
        assertThat(movimientos).extracting(MovimientoInventarioDto::getStockResultante)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("10"), bd("7"), bd("9"), bd("11"));

        // El último resultante coincide con el stock actual del producto.
        assertThat(productoEnBase(id).getStock()).isEqualByComparingTo(movimientos.get(3).getStockResultante());

        assertThat(movimientos).allSatisfy(m -> {
            assertThat(m.getUsuarioNombre()).isEqualTo("user" + sufijo);
            assertThat(m.getUsuarioNombreCompleto()).isEqualTo("Ana Pérez");
            assertThat(m.getMotivo()).isNotBlank();
        });
    }

    @Test
    @DisplayName("El historial filtra por tipo de movimiento")
    void historialPorTipo() {
        Long id = crearProducto("Arroz", UnidadMedida.UNIDAD);
        inventarioService.registrarEntrada(id, bd("10"), "Compra");
        inventarioService.registrarSalida(id, bd("3"), "Venta");
        inventarioService.registrarSalida(id, bd("1"), "Venta");
        inventarioService.registrarAjuste(id, bd("-1"), MotivoAjusteInventario.OTRO, "Rotura");

        assertThat(inventarioService.consultarMovimientos(
                new FiltroMovimientos(id, TipoMovimientoInventario.SALIDA, null, null)))
                .extracting(MovimientoInventarioDto::getCantidad)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(bd("-1"), bd("-3"));

        assertThat(inventarioService.consultarMovimientos(
                new FiltroMovimientos(id, TipoMovimientoInventario.AJUSTE, null, null)))
                .singleElement()
                .satisfies(m -> assertThat(m.getMotivoAjuste()).isEqualTo(MotivoAjusteInventario.OTRO));

        assertThat(inventarioService.consultarMovimientos(
                new FiltroMovimientos(id, TipoMovimientoInventario.ENTRADA, null, null))).hasSize(1);
    }

    @Test
    @DisplayName("El historial filtra por rango de fechas, con cualquiera de los extremos o ambos")
    void historialPorFechas() {
        Long id = crearProducto("Fideos", UnidadMedida.UNIDAD);
        inventarioService.registrarEntrada(id, bd("5"), "Compra");
        LocalDate hoy = LocalDate.now();

        assertThat(inventarioService.consultarMovimientos(new FiltroMovimientos(id, null, hoy, hoy))).hasSize(1);
        assertThat(inventarioService.consultarMovimientos(new FiltroMovimientos(id, null, hoy, null))).hasSize(1);
        assertThat(inventarioService.consultarMovimientos(new FiltroMovimientos(id, null, null, hoy))).hasSize(1);

        assertThat(inventarioService.consultarMovimientos(
                new FiltroMovimientos(id, null, hoy.plusDays(1), null))).isEmpty();
        assertThat(inventarioService.consultarMovimientos(
                new FiltroMovimientos(id, null, null, hoy.minusDays(1)))).isEmpty();
        assertThat(inventarioService.consultarMovimientos(
                new FiltroMovimientos(id, null, hoy.minusDays(7), hoy.minusDays(1)))).isEmpty();
    }

    @Test
    @DisplayName("Sin filtros el historial trae los movimientos de todos los productos")
    void historialSinFiltros() {
        Long yerba = crearProducto("Yerba", UnidadMedida.UNIDAD);
        Long queso = crearProducto("Queso", UnidadMedida.KILOGRAMO);
        MovimientoInventarioDto primero = inventarioService.registrarEntrada(yerba, bd("1"), "Compra");
        MovimientoInventarioDto segundo = inventarioService.registrarEntrada(queso, bd("2"), "Compra");

        List<MovimientoInventarioDto> todos = inventarioService.consultarMovimientos(FiltroMovimientos.todos());

        assertThat(todos).extracting(MovimientoInventarioDto::getId)
                .contains(primero.getId(), segundo.getId());
        // Del más reciente al más antiguo.
        assertThat(todos.indexOf(todos.stream().filter(m -> m.getId().equals(segundo.getId())).findFirst().orElseThrow()))
                .isLessThan(todos.indexOf(todos.stream()
                        .filter(m -> m.getId().equals(primero.getId())).findFirst().orElseThrow()));
    }

    @Test
    @DisplayName("Los productos para filtrar el historial son los que tienen movimientos")
    void productosConMovimientos() {
        Long conMovimientos = crearProducto("Yerba", UnidadMedida.UNIDAD);
        Long sinMovimientos = crearProducto("Sal", UnidadMedida.UNIDAD);
        inventarioService.registrarEntrada(conMovimientos, bd("1"), "Compra");

        assertThat(consultaStockService.consultarProductosConMovimientos())
                .extracting(StockProductoDto::productoId)
                .contains(conMovimientos)
                .doesNotContain(sinMovimientos);
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
