package org.gestionComercio.service.impl;

import jakarta.persistence.EntityManager;
import org.gestionComercio.config.AuditorProvider;
import org.gestionComercio.config.JpaAuditingConfig;
import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.dto.compra.DetalleCompraDto;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Compra;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.EstadoCompra;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.CompraMapperImpl;
import org.gestionComercio.mapper.DetalleCompraMapperImpl;
import org.gestionComercio.mapper.MovimientoInventarioMapperImpl;
import org.gestionComercio.mapper.ProductoMapperImpl;
import org.gestionComercio.mapper.ProveedorMapperImpl;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.MovimientoInventarioRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.CompraService;
import org.gestionComercio.service.InventarioService;
import org.gestionComercio.support.PostgresTestcontainersConfig;
import org.gestionComercio.validation.CompraValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el registro de compras contra un PostgreSQL real (Testcontainers) para verificar que
 * compra, stock y caja se confirmen o se reviertan juntos.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        PostgresTestcontainersConfig.class,
        CompraServiceImpl.class,
        CompraValidator.class,
        CompraMapperImpl.class,
        DetalleCompraMapperImpl.class,
        ProveedorMapperImpl.class,
        ProductoMapperImpl.class,
        InventarioServiceImpl.class,
        InventarioComprobanteServiceImpl.class,
        RegistradorMovimientosInventario.class,
        MovimientoInventarioMapperImpl.class,
        SesionUsuario.class,
        JpaAuditingConfig.class,
        AuditorProvider.class
})
class CompraServiceIntegrationTest {

    private static final AtomicLong SECUENCIA = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private CompraService compraService;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private MovimientoCajaRepository movimientoCajaRepository;

    @Autowired
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Autowired
    private SesionUsuario sesionUsuario;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaccion;

    private String sufijo;

    private Usuario usuario;
    private Categoria categoria;
    private Marca marca;
    private Proveedor proveedor;

    @BeforeEach
    void setUp() {
        transaccion = new TransactionTemplate(transactionManager);
        sesionUsuario.cerrarSesion();

        sufijo = String.valueOf(SECUENCIA.incrementAndGet());

        usuario = transaccion.execute(status -> {
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

    private Long abrirCaja(String saldoInicial) {
        return transaccion.execute(status -> {
            Caja caja = Caja.builder()
                    .fechaApertura(LocalDateTime.now())
                    .saldoInicial(new BigDecimal(saldoInicial))
                    .estadoCaja(EstadoCaja.ABIERTA)
                    .usuario(entityManager.merge(usuario))
                    .usuarioApertura(entityManager.merge(usuario))
                    .build();
            entityManager.persist(caja);
            return caja.getId();
        });
    }

    private void cerrarCaja(Long cajaId) {
        transaccion.executeWithoutResult(status -> {
            Caja caja = entityManager.find(Caja.class, cajaId);
            caja.setEstadoCaja(EstadoCaja.CERRADA);
            caja.setFechaCierre(LocalDateTime.now());
            caja.setUsuarioCierre(caja.getUsuario());
        });
    }

    private Long crearProducto(String nombre, String stock, UnidadMedida unidad, boolean manejaStock) {
        Long id = transaccion.execute(status -> {
            Producto producto = Producto.builder()
                    .codigo(nombre + sufijo).nombre(nombre + " " + sufijo).unidadMedida(unidad)
                    .precioVenta(new BigDecimal("2000.00"))
                    .precioCompra(new BigDecimal("1000.00"))
                    .manejaStock(manejaStock)
                    .categoria(entityManager.merge(categoria))
                    .marca(entityManager.merge(marca))
                    .proveedor(entityManager.merge(proveedor))
                    .build();
            entityManager.persist(producto);
            return producto.getId();
        });

        if (new BigDecimal(stock).signum() > 0) {
            inventarioService.registrarEntrada(id, new BigDecimal(stock), "Stock inicial");
        }

        return id;
    }

    private Producto productoDe(Long productoId) {
        return transaccion.execute(status -> entityManager.find(Producto.class, productoId));
    }

    private BigDecimal stockDe(Long productoId) {
        return productoDe(productoId).getStock();
    }

    private List<MovimientoCaja> movimientosDeCaja(Long cajaId) {
        return movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(cajaId);
    }

    private long cantidadDeCompras() {
        return transaccion.execute(status -> entityManager
                .createQuery("select count(c) from Compra c where c.proveedor.id = :id", Long.class)
                .setParameter("id", proveedor.getId())
                .getSingleResult());
    }

    private static DetalleCompraDto renglon(Long productoId, String cantidad, String precio) {
        return DetalleCompraDto.builder()
                .productoId(productoId)
                .cantidad(new BigDecimal(cantidad))
                .precioCompra(new BigDecimal(precio))
                .build();
    }

    private CompraDto compra(FormaPago formaPago, DetalleCompraDto... renglones) {
        return CompraDto.builder()
                .proveedorId(proveedor.getId())
                .formaPago(formaPago)
                .detalles(new ArrayList<>(List.of(renglones)))
                .build();
    }

    // ------------------------------------------------------------------
    // Registro
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una compra recibida en efectivo guarda renglones, suma stock y paga desde la caja")
    void compraEnEfectivo() {
        Long cajaId = abrirCaja("50000.00");
        Long gaseosa = crearProducto("Gaseosa", "5", UnidadMedida.UNIDAD, true);
        Long queso = crearProducto("Queso", "0", UnidadMedida.KILOGRAMO, true);

        CompraDto dto = compra(FormaPago.EFECTIVO, renglon(gaseosa, "24", "900"), renglon(queso, "2.5", "6000.00"));
        dto.setNumeroFactura("  A-0001-00000123 ");

        CompraDto guardada = compraService.registrar(dto);

        // 24 × 900 = 21600; 2,5 × 6000 = 15000; total 36600
        assertThat(guardada.getId()).isNotNull();
        assertThat(guardada.getFecha()).isNotNull();
        assertThat(guardada.getNumeroFactura()).isEqualTo("A-0001-00000123");
        assertThat(guardada.getProveedorNombre()).isEqualTo("Proveedor " + sufijo);
        assertThat(guardada.getUsuarioId()).isEqualTo(usuario.getId());
        assertThat(guardada.getCajaId()).isEqualTo(cajaId);
        assertThat(guardada.getEstadoCompra()).isEqualTo(EstadoCompra.RECIBIDA);
        assertThat(guardada.getSubtotal()).isEqualByComparingTo("36600");
        assertThat(guardada.getTotal()).isEqualByComparingTo("36600");
        assertThat(guardada.getDetalles()).extracting(DetalleCompraDto::getSubtotal)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("21600"), new BigDecimal("15000"));

        assertThat(stockDe(gaseosa)).isEqualByComparingTo("29");
        assertThat(stockDe(queso)).isEqualByComparingTo("2.5");
        assertThat(movimientoInventarioRepository.findOriginalesDelComprobante(
                OrigenMovimientoInventario.COMPRA, guardada.getId())).hasSize(2);

        List<MovimientoCaja> movimientos = movimientosDeCaja(cajaId);
        assertThat(movimientos).hasSize(1);
        assertThat(movimientos.get(0).getTipo()).isEqualTo(TipoMovimientoCaja.PAGO_PROVEEDOR);
        assertThat(movimientos.get(0).getImporte()).isEqualByComparingTo("36600");
        assertThat(movimientos.get(0).getDescripcion()).contains("A-0001-00000123", "Proveedor " + sufijo);

        // Sin pedirlo, no cambia el precio de compra del producto.
        assertThat(productoDe(gaseosa).getPrecioCompra()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("Una compra con otra forma de pago no necesita caja ni la mueve")
    void compraPorTransferenciaSinCaja() {
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);

        CompraDto guardada = compraService.registrar(compra(FormaPago.TRANSFERENCIA, renglon(gaseosa, "10", "900")));

        assertThat(guardada.getCajaId()).isNull();
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Con actualizarPreciosCompra el precio del renglón pasa a ser el precio de compra del producto")
    void actualizaPrecioDeCompra() {
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);
        CompraDto dto = compra(FormaPago.TRANSFERENCIA, renglon(gaseosa, "10", "1250.50"));
        dto.setActualizarPreciosCompra(true);

        compraService.registrar(dto);

        assertThat(productoDe(gaseosa).getPrecioCompra()).isEqualByComparingTo("1250.50");
        assertThat(productoDe(gaseosa).getPrecioVenta()).isEqualByComparingTo("2000");
    }

    @Test
    @DisplayName("Una compra pendiente no mueve stock hasta recibirla")
    void pendienteYRecibir() {
        Long gaseosa = crearProducto("Gaseosa", "5", UnidadMedida.UNIDAD, true);
        CompraDto dto = compra(FormaPago.CUENTA_CORRIENTE, renglon(gaseosa, "10", "900"));
        dto.setEstadoCompra(EstadoCompra.PENDIENTE);

        Long compraId = compraService.registrar(dto).getId();

        assertThat(stockDe(gaseosa)).isEqualByComparingTo("5");

        CompraDto recibida = compraService.recibir(compraId);

        assertThat(recibida.getEstadoCompra()).isEqualTo(EstadoCompra.RECIBIDA);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("15");

        assertThatThrownBy(() -> compraService.recibir(compraId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pendientes");
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("Los productos que no manejan stock se compran sin movimiento de inventario")
    void productoSinControlDeStock() {
        Long servicio = crearProducto("Servicio", "0", UnidadMedida.UNIDAD, false);

        CompraDto guardada = compraService.registrar(compra(FormaPago.TRANSFERENCIA, renglon(servicio, "1", "500")));

        assertThat(movimientoInventarioRepository.findOriginalesDelComprobante(
                OrigenMovimientoInventario.COMPRA, guardada.getId())).isEmpty();
    }

    @Test
    @DisplayName("No se registra dos veces la misma factura del mismo proveedor; anular la libera")
    void facturaDuplicada() {
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);
        CompraDto primera = compra(FormaPago.TRANSFERENCIA, renglon(gaseosa, "1", "900"));
        primera.setNumeroFactura("B-0002-00000777");
        Long primeraId = compraService.registrar(primera).getId();

        CompraDto repetida = compra(FormaPago.TRANSFERENCIA, renglon(gaseosa, "1", "900"));
        repetida.setNumeroFactura("b-0002-00000777");

        assertThatThrownBy(() -> compraService.registrar(repetida))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getValidationResult().getErrors())
                                .extracting(err -> err.getField())
                                .containsExactly("numeroFactura"));
        assertThat(cantidadDeCompras()).isEqualTo(1);

        compraService.anular(primeraId, "Cargada con errores");

        CompraDto otraVez = compra(FormaPago.TRANSFERENCIA, renglon(gaseosa, "1", "900"));
        otraVez.setNumeroFactura("B-0002-00000777");

        assertThat(compraService.registrar(otraVez).getId()).isNotNull();
    }

    @Test
    @DisplayName("En efectivo sin caja abierta no se guarda nada")
    void efectivoSinCaja() {
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);

        assertThatThrownBy(() -> compraService.registrar(compra(FormaPago.EFECTIVO, renglon(gaseosa, "1", "900"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("caja");

        assertThat(cantidadDeCompras()).isZero();
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("En efectivo sin saldo suficiente en caja no se guarda nada")
    void efectivoSinSaldo() {
        Long cajaId = abrirCaja("1000.00");
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);

        assertThatThrownBy(() -> compraService.registrar(compra(FormaPago.EFECTIVO, renglon(gaseosa, "2", "900"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("efectivo suficiente");

        assertThat(cantidadDeCompras()).isZero();
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("0");
        assertThat(movimientosDeCaja(cajaId)).isEmpty();
    }

    @Test
    @DisplayName("Datos inválidos se rechazan con ValidationException y no tocan el stock")
    void datosInvalidos() {
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);
        CompraDto dto = compra(FormaPago.TRANSFERENCIA, renglon(gaseosa, "0", "900"));
        dto.setProveedorId(null);

        assertThatThrownBy(() -> compraService.registrar(dto))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getValidationResult().getErrors())
                                .extracting(err -> err.getField())
                                .containsExactlyInAnyOrder("proveedorId", "detalles[0].cantidad"));

        assertThat(stockDe(gaseosa)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Cada operación exige su permiso")
    void permisos() {
        EnumSet<PermisoCodigo> sinCompras = EnumSet.allOf(PermisoCodigo.class);
        sinCompras.removeAll(EnumSet.of(PermisoCodigo.COMPRA_VER, PermisoCodigo.COMPRA_CREAR, PermisoCodigo.COMPRA_ANULAR));
        sesionUsuario.iniciarSesion(usuario, sinCompras);

        assertThatThrownBy(() -> compraService.registrar(compra(FormaPago.EFECTIVO)))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> compraService.recibir(1L)).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> compraService.anular(1L, "x")).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> compraService.obtener(1L)).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> compraService.listar(null, null, null, null))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> compraService.listarProveedores()).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> compraService.buscarProductos("")).isInstanceOf(AuthorizationException.class);
    }

    // ------------------------------------------------------------------
    // Anulación
    // ------------------------------------------------------------------

    private EstadoCompra estadoDe(Long compraId) {
        return transaccion.execute(status -> entityManager.find(Compra.class, compraId).getEstadoCompra());
    }

    @Test
    @DisplayName("Anular una compra en efectivo descuenta el stock, reintegra el efectivo y la deja anulada")
    void anularCompraEnEfectivo() {
        Long cajaId = abrirCaja("50000.00");
        Long gaseosa = crearProducto("Gaseosa", "5", UnidadMedida.UNIDAD, true);
        Long compraId = compraService.registrar(compra(FormaPago.EFECTIVO, renglon(gaseosa, "10", "900"))).getId();

        CompraDto anulada = compraService.anular(compraId, "  Mercadería devuelta  ");

        assertThat(anulada.getEstadoCompra()).isEqualTo(EstadoCompra.ANULADA);
        assertThat(anulada.getMotivoAnulacion()).isEqualTo("Mercadería devuelta");
        assertThat(anulada.getFechaAnulacion()).isNotNull();
        assertThat(anulada.getUsuarioAnulacionNombreCompleto()).isEqualTo("Ana Pérez");
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("5");

        List<MovimientoCaja> movimientos = movimientosDeCaja(cajaId);
        assertThat(movimientos).extracting(MovimientoCaja::getTipo)
                .containsExactly(TipoMovimientoCaja.PAGO_PROVEEDOR, TipoMovimientoCaja.ANULACION_COMPRA);
        assertThat(movimientos.get(1).getImporte()).isEqualByComparingTo("9000");

        assertThatThrownBy(() -> compraService.anular(compraId, "Otra vez"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya está anulada");
    }

    @Test
    @DisplayName("Anular una compra pendiente no toca el stock")
    void anularPendiente() {
        Long gaseosa = crearProducto("Gaseosa", "5", UnidadMedida.UNIDAD, true);
        CompraDto dto = compra(FormaPago.CUENTA_CORRIENTE, renglon(gaseosa, "10", "900"));
        dto.setEstadoCompra(EstadoCompra.PENDIENTE);
        Long compraId = compraService.registrar(dto).getId();

        compraService.anular(compraId, "El proveedor no la entregó");

        assertThat(estadoDe(compraId)).isEqualTo(EstadoCompra.ANULADA);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("No se anula si revertir dejaría stock negativo, y no se guarda nada")
    void anularConStockConsumido() {
        Long cajaId = abrirCaja("50000.00");
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);
        Long compraId = compraService.registrar(compra(FormaPago.EFECTIVO, renglon(gaseosa, "10", "900"))).getId();
        inventarioService.registrarSalida(gaseosa, new BigDecimal("8"), "Vendido");

        assertThatThrownBy(() -> compraService.anular(compraId, "Error"))
                .isInstanceOf(BusinessException.class);

        assertThat(estadoDe(compraId)).isEqualTo(EstadoCompra.RECIBIDA);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("2");
        assertThat(movimientosDeCaja(cajaId)).hasSize(1);
    }

    @Test
    @DisplayName("Si la caja de la compra se cerró, el reintegro va a la caja abierta del usuario")
    void anularConCajaCerrada() {
        Long cajaCompra = abrirCaja("50000.00");
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);
        Long compraId = compraService.registrar(compra(FormaPago.EFECTIVO, renglon(gaseosa, "1", "900"))).getId();
        cerrarCaja(cajaCompra);

        assertThatThrownBy(() -> compraService.anular(compraId, "Error"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Abra una caja");
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("1");

        Long cajaNueva = abrirCaja("0.00");
        compraService.anular(compraId, "Error");

        assertThat(movimientosDeCaja(cajaNueva)).extracting(MovimientoCaja::getTipo)
                .containsExactly(TipoMovimientoCaja.ANULACION_COMPRA);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Anular exige motivo")
    void anularSinMotivo() {
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);
        Long compraId = compraService.registrar(compra(FormaPago.TRANSFERENCIA, renglon(gaseosa, "1", "900"))).getId();

        assertThatThrownBy(() -> compraService.anular(compraId, "  "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motivo");
        assertThat(estadoDe(compraId)).isEqualTo(EstadoCompra.RECIBIDA);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Obtener trae los renglones y listar filtra por estado y proveedor")
    void obtenerYListar() {
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);
        Long recibidaId = compraService.registrar(compra(FormaPago.TRANSFERENCIA, renglon(gaseosa, "1", "900"))).getId();
        CompraDto pendiente = compra(FormaPago.TRANSFERENCIA, renglon(gaseosa, "2", "900"));
        pendiente.setEstadoCompra(EstadoCompra.PENDIENTE);
        Long pendienteId = compraService.registrar(pendiente).getId();

        CompraDto leida = compraService.obtener(recibidaId);
        assertThat(leida.getDetalles()).hasSize(1);
        assertThat(leida.getDetalles().get(0).getProductoNombre()).isEqualTo("Gaseosa " + sufijo);

        LocalDate hoy = LocalDate.now();
        assertThat(compraService.listar(hoy, hoy, null, proveedor.getId()))
                .extracting(CompraDto::getId).containsExactly(pendienteId, recibidaId);
        assertThat(compraService.listar(null, null, EstadoCompra.PENDIENTE, proveedor.getId()))
                .extracting(CompraDto::getId).containsExactly(pendienteId);
        assertThatThrownBy(() -> compraService.listar(hoy, hoy.minusDays(1), null, null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Las consultas de la pantalla sólo devuelven proveedores y productos activos")
    void consultasDePantalla() {
        Long gaseosa = crearProducto("Gaseosa", "0", UnidadMedida.UNIDAD, true);
        transaccion.executeWithoutResult(status ->
                entityManager.find(Producto.class, gaseosa).setEstadoRegistro(EstadoRegistro.INACTIVO));

        assertThat(compraService.listarProveedores()).extracting(p -> p.getId()).contains(proveedor.getId());
        assertThat(compraService.buscarProductos("Gaseosa " + sufijo)).isEmpty();
    }

    @Test
    @DisplayName("Con sólo COMPRA_VER se listan los proveedores (filtro del listado) pero no se buscan productos")
    void proveedoresConSoloCompraVer() {
        sesionUsuario.iniciarSesion(usuario, EnumSet.of(PermisoCodigo.COMPRA_VER));

        assertThat(compraService.listarProveedores()).extracting(p -> p.getId()).contains(proveedor.getId());
        assertThatThrownBy(() -> compraService.buscarProductos("")).isInstanceOf(AuthorizationException.class);
    }

}
