package org.gestionComercio.service.impl;

import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.gestionComercio.config.AuditorProvider;
import org.gestionComercio.config.JpaAuditingConfig;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.ResumenVentas;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.dto.venta.VentasPorCaja;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.entity.Venta;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.arca.AlmacenClaveEnMemoria;
import org.gestionComercio.arca.ArcaConfig;
import org.gestionComercio.arca.ClaveCertificadoArca;
import org.gestionComercio.mapper.ClienteMapperImpl;
import org.gestionComercio.mapper.ComprobanteArcaMapperImpl;
import org.gestionComercio.mapper.ConfiguracionMapperImpl;
import org.gestionComercio.mapper.DetalleVentaMapperImpl;
import org.gestionComercio.mapper.EmpresaMapperImpl;
import org.gestionComercio.mapper.ProductoMapperImpl;
import org.gestionComercio.mapper.MovimientoInventarioMapperImpl;
import org.gestionComercio.mapper.VentaMapperImpl;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.MovimientoInventarioRepository;
import org.gestionComercio.repository.NumeradorComprobanteRepository;
import org.gestionComercio.repository.VentaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ComprobanteVentaService;
import org.gestionComercio.service.InventarioService;
import org.gestionComercio.service.VentaService;
import org.gestionComercio.licencia.ModulosTodosHabilitados;
import org.gestionComercio.service.impl.arca.ConfiguracionArcaVigente;
import org.gestionComercio.service.impl.arca.EmisionComprobantesArca;
import org.gestionComercio.support.PostgresTestcontainersConfig;
import org.gestionComercio.validation.VentaValidator;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba el registro de ventas contra un PostgreSQL real (Testcontainers) para verificar que
 * venta, stock y caja se confirmen o se reviertan juntos.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        PostgresTestcontainersConfig.class,
        VentaServiceImpl.class,
        ControlEfectivoCaja.class,
        ConfiguracionServiceImpl.class,
        ConfiguracionMapperImpl.class,
        NumeradorComprobanteRepository.class,
        ComprobanteVentaServiceImpl.class,
        EmpresaMapperImpl.class,
        VentaValidator.class,
        EmisionComprobantesArca.class,
        ConfiguracionArcaVigente.class,
        ModulosTodosHabilitados.class,
        ClaveCertificadoArca.class,
        AlmacenClaveEnMemoria.class,
        ArcaConfig.class,
        ComprobanteArcaMapperImpl.class,
        VentaMapperImpl.class,
        DetalleVentaMapperImpl.class,
        ProductoMapperImpl.class,
        ClienteMapperImpl.class,
        InventarioServiceImpl.class,
        InventarioComprobanteServiceImpl.class,
        RegistradorMovimientosInventario.class,
        MovimientoInventarioMapperImpl.class,
        SesionUsuario.class,
        JpaAuditingConfig.class,
        AuditorProvider.class
})
class VentaServiceIntegrationTest {

    private static final AtomicLong SECUENCIA = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ComprobanteVentaService comprobanteVentaService;

    @Autowired
    private InventarioService inventarioService;

    @Autowired
    private VentaRepository ventaRepository;

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

    private Long abrirCaja() {
        return transaccion.execute(status -> {
            Caja caja = Caja.builder()
                    .fechaApertura(LocalDateTime.now())
                    .saldoInicial(new BigDecimal("1000.00"))
                    .estadoCaja(EstadoCaja.ABIERTA)
                    .usuario(entityManager.merge(usuario))
                    .usuarioApertura(entityManager.merge(usuario))
                    .build();
            entityManager.persist(caja);
            return caja.getId();
        });
    }

    private Long crearProducto(String nombre, String precio, String stock, UnidadMedida unidad, boolean manejaStock) {
        Long id = transaccion.execute(status -> {
            Producto producto = Producto.builder()
                    .codigo(nombre + sufijo).nombre(nombre + " " + sufijo).unidadMedida(unidad)
                    .precioVenta(new BigDecimal(precio))
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

    private Long crearCliente(EstadoRegistro estado) {
        return transaccion.execute(status -> {
            Cliente cliente = Cliente.builder()
                    .nombre("Juan").apellido("Gómez")
                    .tipoDocumento(TipoDocumento.DNI).numeroDocumento("2" + sufijo)
                    .build();
            cliente.setEstadoRegistro(estado);
            entityManager.persist(cliente);
            return cliente.getId();
        });
    }

    private BigDecimal stockDe(Long productoId) {
        return transaccion.execute(status -> entityManager.find(Producto.class, productoId).getStock());
    }

    private List<MovimientoCaja> movimientosDeCaja(Long cajaId) {
        return movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(cajaId);
    }

    private static DetalleVentaDto renglon(Long productoId, String cantidad) {
        return DetalleVentaDto.builder().productoId(productoId).cantidad(new BigDecimal(cantidad)).build();
    }

    private static VentaDto venta(FormaPago formaPago, DetalleVentaDto... renglones) {
        return VentaDto.builder()
                .formaPago(formaPago)
                .detalles(new ArrayList<>(List.of(renglones)))
                .build();
    }

    // ------------------------------------------------------------------
    // Registro
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una venta en efectivo guarda renglones con precio, descuenta stock e ingresa el total en caja")
    void ventaEnEfectivo() {
        Long cajaId = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long queso = crearProducto("Queso", "8000.00", "5", UnidadMedida.KILOGRAMO, true);

        VentaDto dto = venta(FormaPago.EFECTIVO, renglon(gaseosa, "2"), renglon(queso, "0.375"));
        dto.setDescuento(new BigDecimal("500"));

        VentaDto guardada = ventaService.registrar(dto);

        // 2 × 1500 = 3000; 0,375 × 8000 = 3000; subtotal 6000 − 500 = 5500
        assertThat(guardada.getId()).isNotNull();
        assertThat(guardada.getFecha()).isNotNull();
        assertThat(guardada.getUsuarioId()).isEqualTo(usuario.getId());
        assertThat(guardada.getCajaId()).isEqualTo(cajaId);
        assertThat(guardada.getClienteId()).isNull();
        assertThat(guardada.getClienteNombre()).isEqualTo(VentaDto.CONSUMIDOR_FINAL);
        assertThat(guardada.getEstadoVenta()).isEqualTo(EstadoVenta.PAGADA);
        assertThat(guardada.getSubtotal()).isEqualByComparingTo("6000");
        assertThat(guardada.getDescuento()).isEqualByComparingTo("500");
        assertThat(guardada.getTotal()).isEqualByComparingTo("5500");
        assertThat(guardada.getDetalles()).extracting(DetalleVentaDto::getPrecioUnitario)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("1500"), new BigDecimal("8000"));

        assertThat(stockDe(gaseosa)).isEqualByComparingTo("8");
        assertThat(stockDe(queso)).isEqualByComparingTo("4.625");
        assertThat(movimientoInventarioRepository.findOriginalesDelComprobante(
                OrigenMovimientoInventario.VENTA, guardada.getId())).hasSize(2);

        List<MovimientoCaja> movimientos = movimientosDeCaja(cajaId);
        assertThat(movimientos).hasSize(1);
        assertThat(movimientos.get(0).getTipo()).isEqualTo(TipoMovimientoCaja.VENTA);
        assertThat(movimientos.get(0).getImporte()).isEqualByComparingTo("5500");
        assertThat(guardada.getTipoComprobante()).isEqualTo(TipoComprobante.TICKET);
        assertThat(guardada.getComprobante()).startsWith("Ticket N° ");
        assertThat(movimientos.get(0).getDescripcion()).isEqualTo(guardada.getComprobante());
    }

    @Test
    @DisplayName("El precio queda guardado en el renglón aunque después cambie el precio del producto")
    void precioCongelado() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long ventaId = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))).getId();

        transaccion.executeWithoutResult(status ->
                entityManager.find(Producto.class, gaseosa).setPrecioVenta(new BigDecimal("2000.00")));

        VentaDto leida = ventaService.obtener(ventaId);
        assertThat(leida.getDetalles().get(0).getPrecioUnitario()).isEqualByComparingTo("1500");
        assertThat(leida.getTotal()).isEqualByComparingTo("1500");
    }

    @Test
    @DisplayName("Una venta con otra forma de pago no genera movimiento de caja")
    void ventaConTarjetaNoMueveCaja() {
        Long cajaId = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);

        VentaDto guardada = ventaService.registrar(venta(FormaPago.TARJETA_DEBITO, renglon(gaseosa, "1")));

        assertThat(guardada.getCajaId()).isEqualTo(cajaId);
        assertThat(movimientosDeCaja(cajaId)).isEmpty();
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("9");
    }

    @Test
    @DisplayName("Una venta en cuenta corriente con cliente queda pendiente")
    void cuentaCorrienteQuedaPendiente() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long clienteId = crearCliente(EstadoRegistro.ACTIVO);

        VentaDto dto = venta(FormaPago.CUENTA_CORRIENTE, renglon(gaseosa, "1"));
        dto.setClienteId(clienteId);

        VentaDto guardada = ventaService.registrar(dto);

        assertThat(guardada.getEstadoVenta()).isEqualTo(EstadoVenta.PENDIENTE);
        assertThat(guardada.getClienteId()).isEqualTo(clienteId);
        assertThat(guardada.getClienteNombre()).isEqualTo("Gómez, Juan");
    }

    @Test
    @DisplayName("Los productos que no manejan stock se venden sin movimiento de inventario")
    void productoSinControlDeStock() {
        abrirCaja();
        Long servicio = crearProducto("Servicio", "500.00", "0", UnidadMedida.UNIDAD, false);

        VentaDto guardada = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(servicio, "3")));

        assertThat(guardada.getTotal()).isEqualByComparingTo("1500");
        assertThat(movimientoInventarioRepository.findOriginalesDelComprobante(
                OrigenMovimientoInventario.VENTA, guardada.getId())).isEmpty();
    }

    // ------------------------------------------------------------------
    // Rechazos: no se guarda nada
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Sin stock suficiente informa todos los faltantes juntos y no guarda venta, stock ni caja")
    void sinStockNoGuardaNada() {
        Long cajaId = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "1", UnidadMedida.UNIDAD, true);
        Long queso = crearProducto("Queso", "8000.00", "0.5", UnidadMedida.KILOGRAMO, true);
        Long yerba = crearProducto("Yerba", "3000.00", "10", UnidadMedida.UNIDAD, true);
        long ventasAntes = ventaRepository.count();

        assertThatThrownBy(() -> ventaService.registrar(venta(FormaPago.EFECTIVO,
                renglon(gaseosa, "2"), renglon(queso, "1"), renglon(yerba, "1"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Gaseosa " + sufijo)
                .hasMessageContaining("Queso " + sufijo)
                .hasMessageNotContaining("Yerba " + sufijo);

        assertThat(ventaRepository.count()).isEqualTo(ventasAntes);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("1");
        assertThat(stockDe(queso)).isEqualByComparingTo("0.5");
        assertThat(stockDe(yerba)).isEqualByComparingTo("10");
        assertThat(movimientosDeCaja(cajaId)).isEmpty();
    }

    @Test
    @DisplayName("Sin caja abierta no se puede registrar una venta")
    void sinCajaAbierta() {
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        long ventasAntes = ventaRepository.count();

        assertThatThrownBy(() -> ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("caja abierta");

        assertThat(ventaRepository.count()).isEqualTo(ventasAntes);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("10");
    }

    // ------------------------------------------------------------------
    // Configuración del sistema
    // ------------------------------------------------------------------

    /** Cambia la fila única de configuración; {@link #restaurarConfiguracion} la deja como estaba. */
    private void configurar(String asignaciones) {
        transaccion.executeWithoutResult(status ->
                entityManager.createNativeQuery("update configuracion set " + asignaciones).executeUpdate());
    }

    @AfterEach
    void restaurarConfiguracion() {
        configurar("venta_exige_caja = true, redondeo = 'SIN_REDONDEO'");
    }

    @Test
    @DisplayName("Si la configuración no exige caja, se vende sin caja: no hay movimiento de caja y se anula sin devolver efectivo")
    void ventaSinCajaPermitida() {
        configurar("venta_exige_caja = false");
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        long movimientosAntes = movimientoCajaRepository.count();

        VentaDto guardada = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "2")));

        assertThat(guardada.getCajaId()).isNull();
        assertThat(guardada.getTotal()).isEqualByComparingTo("3000");
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("8");
        assertThat(movimientoCajaRepository.count()).isEqualTo(movimientosAntes);

        // Lo vendido sin caja aparece en los totales del día.
        assertThat(ventaService.ventasDelDiaPropias().total()).isEqualByComparingTo("3000");
        assertThat(ventaService.ventasDelDiaPorCaja())
                .filteredOn(VentasPorCaja::sinCaja)
                .filteredOn(v -> v.usuarioNombreCompleto().equals("Ana Pérez"))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.cantidad()).isGreaterThanOrEqualTo(1L);
                    assertThat(v.total()).isGreaterThanOrEqualTo(new BigDecimal("3000"));
                });

        // Se lee y se imprime aunque no tenga caja.
        assertThat(ventaService.obtener(guardada.getId()).getCajaId()).isNull();
        assertThat(comprobanteVentaService.generarPdf(guardada.getId())).isNotEmpty();

        VentaDto anulada = ventaService.anular(guardada.getId(), "Prueba");

        assertThat(anulada.getEstadoVenta()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("10");
        assertThat(movimientoCajaRepository.count()).isEqualTo(movimientosAntes);
    }

    @Test
    @DisplayName("Con caja abierta, la venta va a la caja aunque la configuración no la exija")
    void ventaConCajaSinExigirla() {
        configurar("venta_exige_caja = false");
        Long cajaId = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);

        VentaDto guardada = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1")));

        assertThat(guardada.getCajaId()).isEqualTo(cajaId);
        assertThat(movimientosDeCaja(cajaId)).hasSize(1);
    }

    @Test
    @DisplayName("El total se redondea hacia abajo según la configuración y la diferencia se suma al descuento")
    void redondeoDelTotal() {
        configurar("redondeo = 'UN_PESO'");
        Long cajaId = abrirCaja();
        Long yerba = crearProducto("Yerba", "1234.56", "10", UnidadMedida.UNIDAD, true);

        VentaDto dto = venta(FormaPago.EFECTIVO, renglon(yerba, "1"));
        dto.setDescuento(new BigDecimal("10.00"));

        VentaDto guardada = ventaService.registrar(dto);

        // 1234,56 − 10 = 1224,56 → 1224,00; descuento 10 + 0,56
        assertThat(guardada.getSubtotal()).isEqualByComparingTo("1234.56");
        assertThat(guardada.getDescuento()).isEqualByComparingTo("10.56");
        assertThat(guardada.getTotal()).isEqualByComparingTo("1224.00");
        assertThat(movimientosDeCaja(cajaId).get(0).getImporte()).isEqualByComparingTo("1224.00");
    }

    // ------------------------------------------------------------------
    // Comprobante
    // ------------------------------------------------------------------

    private VentaDto registrarComprobante(TipoComprobante tipo, Long productoId) {
        VentaDto dto = venta(FormaPago.TARJETA_DEBITO, renglon(productoId, "1"));
        dto.setTipoComprobante(tipo);
        return ventaService.registrar(dto);
    }

    @Test
    @DisplayName("La numeración es correlativa dentro de cada tipo de comprobante e independiente entre tipos")
    void numeracionCorrelativaPorTipo() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);

        VentaDto ticket1 = registrarComprobante(TipoComprobante.TICKET, gaseosa);
        VentaDto remito1 = registrarComprobante(TipoComprobante.REMITO, gaseosa);
        VentaDto ticket2 = registrarComprobante(TipoComprobante.TICKET, gaseosa);
        VentaDto remito2 = registrarComprobante(TipoComprobante.REMITO, gaseosa);

        assertThat(ticket2.getNumeroComprobante()).isEqualTo(ticket1.getNumeroComprobante() + 1);
        assertThat(remito2.getNumeroComprobante()).isEqualTo(remito1.getNumeroComprobante() + 1);
        assertThat(remito1.getComprobante()).startsWith("Remito N° ");

        VentaDto leida = ventaService.obtener(ticket1.getId());
        assertThat(leida.getTipoComprobante()).isEqualTo(TipoComprobante.TICKET);
        assertThat(leida.getNumeroComprobante()).isEqualTo(ticket1.getNumeroComprobante());
    }

    @Test
    @DisplayName("Sin tipo de comprobante la venta se emite como ticket")
    void ticketPorDefecto() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);

        VentaDto guardada = registrarComprobante(null, gaseosa);

        assertThat(guardada.getTipoComprobante()).isEqualTo(TipoComprobante.TICKET);
        assertThat(guardada.getNumeroComprobante()).isPositive();
    }

    @Test
    @DisplayName("Una venta rechazada no consume número de comprobante")
    void ventaRechazadaNoConsumeNumero() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "1", UnidadMedida.UNIDAD, true);
        Long yerba = crearProducto("Yerba", "3000.00", "10", UnidadMedida.UNIDAD, true);

        VentaDto antes = registrarComprobante(TipoComprobante.TICKET, yerba);

        // El número se reserva antes de descontar el stock; al faltar stock se revierte todo.
        assertThatThrownBy(() -> ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "5"))))
                .isInstanceOf(BusinessException.class);

        VentaDto despues = registrarComprobante(TipoComprobante.TICKET, yerba);

        assertThat(despues.getNumeroComprobante()).isEqualTo(antes.getNumeroComprobante() + 1);
    }

    @Test
    @DisplayName("Sin facturación electrónica configurada no se pueden emitir facturas y no se guarda nada")
    void facturaRechazada() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        long ventasAntes = ventaRepository.count();

        assertThatThrownBy(() -> registrarComprobante(TipoComprobante.FACTURA_B, gaseosa))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("facturación electrónica");
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("10");

        assertThat(ventaRepository.count()).isEqualTo(ventasAntes);
    }

    @Test
    @DisplayName("Ventas simultáneas del mismo tipo obtienen números distintos y consecutivos")
    void numeracionConcurrente() throws Exception {
        abrirCaja();
        int cantidad = 6;

        // Un producto por venta: dos ventas simultáneas del mismo producto chocan en el stock
        // (bloqueo optimista), y acá sólo interesa la numeración.
        List<Long> productos = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            productos.add(crearProducto("Producto" + i, "100.00", "10", UnidadMedida.UNIDAD, true));
        }

        ExecutorService ejecutor = Executors.newFixedThreadPool(cantidad);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Long>> numeros = new ArrayList<>();

        try {
            for (Long producto : productos) {
                numeros.add(ejecutor.submit(() -> {
                    largada.await();
                    return registrarComprobante(TipoComprobante.TICKET, producto).getNumeroComprobante();
                }));
            }

            largada.countDown();

            List<Long> obtenidos = new ArrayList<>();
            for (Future<Long> numero : numeros) {
                obtenidos.add(numero.get(30, TimeUnit.SECONDS));
            }

            long minimo = obtenidos.stream().mapToLong(Long::longValue).min().orElseThrow();
            assertThat(obtenidos).doesNotHaveDuplicates()
                    .allSatisfy(n -> assertThat(n).isBetween(minimo, minimo + cantidad - 1));
        } finally {
            ejecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("El comprobante en PDF muestra la empresa, el número y, si la venta está anulada, ANULADA")
    void comprobantePdf() throws Exception {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        VentaDto venta = registrarComprobante(TipoComprobante.TICKET, gaseosa);

        // Sólo con VENTA_CREAR: el vendedor imprime al terminar la venta.
        sesionUsuario.iniciarSesion(usuario, EnumSet.of(PermisoCodigo.VENTA_CREAR));

        String pagada = textoPdf(comprobanteVentaService.generarPdf(venta.getId()));

        assertThat(pagada)
                .contains(venta.getComprobante().replace("Ticket ", ""))
                .contains("TICKET")
                .contains("Gaseosa " + sufijo)
                .doesNotContain("ANULADA");

        sesionUsuario.iniciarSesion(usuario, EnumSet.allOf(PermisoCodigo.class));
        ventaService.anular(venta.getId(), "Cliente arrepentido");

        String anulada = textoPdf(comprobanteVentaService.generarPdf(venta.getId()));

        assertThat(anulada).contains("ANULADA").contains("Cliente arrepentido");

        sesionUsuario.iniciarSesion(usuario, EnumSet.of(PermisoCodigo.CAJA_VER));

        assertThatThrownBy(() -> comprobanteVentaService.generarPdf(venta.getId()))
                .isInstanceOf(AuthorizationException.class);
    }

    private static String textoPdf(byte[] pdf) throws Exception {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    @Test
    @DisplayName("Datos inválidos se rechazan con ValidationException y no tocan el stock")
    void datosInvalidos() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long clienteBaja = crearCliente(EstadoRegistro.INACTIVO);

        VentaDto dto = venta(FormaPago.EFECTIVO, renglon(gaseosa, "0"));
        dto.setClienteId(clienteBaja);

        assertThatThrownBy(() -> ventaService.registrar(dto))
                .isInstanceOfSatisfying(ValidationException.class, e ->
                        assertThat(e.getValidationResult().getErrors())
                                .extracting(err -> err.getField())
                                .containsExactlyInAnyOrder("clienteId", "detalles[0].cantidad"));

        assertThat(stockDe(gaseosa)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Registrar exige VENTA_CREAR y consultar exige VENTA_VER")
    void permisos() {
        EnumSet<PermisoCodigo> sinVentas = EnumSet.allOf(PermisoCodigo.class);
        sinVentas.remove(PermisoCodigo.VENTA_CREAR);
        sinVentas.remove(PermisoCodigo.VENTA_VER);
        sesionUsuario.iniciarSesion(usuario, sinVentas);

        assertThatThrownBy(() -> ventaService.registrar(venta(FormaPago.EFECTIVO)))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> ventaService.obtener(1L)).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> ventaService.listar(null, null, null)).isInstanceOf(AuthorizationException.class);
    }

    // ------------------------------------------------------------------
    // Anulación
    // ------------------------------------------------------------------

    private void cerrarCaja(Long cajaId) {
        transaccion.executeWithoutResult(status -> {
            Caja caja = entityManager.find(Caja.class, cajaId);
            caja.setEstadoCaja(EstadoCaja.CERRADA);
            caja.setFechaCierre(LocalDateTime.now());
            caja.setUsuarioCierre(caja.getUsuario());
        });
    }

    private void retirar(Long cajaId, String importe) {
        transaccion.executeWithoutResult(status -> entityManager.persist(MovimientoCaja.builder()
                .fecha(LocalDateTime.now())
                .tipo(TipoMovimientoCaja.RETIRO)
                .descripcion("Retiro")
                .importe(new BigDecimal(importe))
                .caja(entityManager.find(Caja.class, cajaId))
                .usuario(entityManager.merge(usuario))
                .build()));
    }

    private EstadoVenta estadoDe(Long ventaId) {
        return transaccion.execute(status -> entityManager.find(Venta.class, ventaId).getEstadoVenta());
    }

    @Test
    @DisplayName("Anular una venta en efectivo devuelve el stock, registra el egreso en caja y la deja anulada")
    void anularVentaEnEfectivo() {
        Long cajaId = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long servicio = crearProducto("Servicio", "500.00", "0", UnidadMedida.UNIDAD, false);
        Long ventaId = ventaService.registrar(venta(FormaPago.EFECTIVO,
                renglon(gaseosa, "2"), renglon(servicio, "1"))).getId();

        VentaDto anulada = ventaService.anular(ventaId, "  Error en la carga  ");

        assertThat(anulada.getEstadoVenta()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(anulada.getMotivoAnulacion()).isEqualTo("Error en la carga");
        assertThat(anulada.getFechaAnulacion()).isNotNull();
        assertThat(anulada.getUsuarioAnulacionNombreCompleto()).isEqualTo("Ana Pérez");
        assertThat(anulada.getDetalles()).hasSize(2);

        assertThat(stockDe(gaseosa)).isEqualByComparingTo("10");
        assertThat(movimientoInventarioRepository.existsByOrigenAndOrigenIdAndMovimientoRevertidoIsNotNull(
                OrigenMovimientoInventario.VENTA, ventaId)).isTrue();

        List<MovimientoCaja> movimientos = movimientosDeCaja(cajaId);
        assertThat(movimientos).extracting(MovimientoCaja::getTipo)
                .containsExactly(TipoMovimientoCaja.VENTA, TipoMovimientoCaja.ANULACION_VENTA);
        assertThat(movimientos.get(1).getImporte()).isEqualByComparingTo("3500");
        assertThat(movimientos.get(1).getDescripcion()).isEqualTo("Anulación de venta " + anulada.getComprobante());

        // Sigue en el listado, marcada como anulada.
        assertThat(ventaService.listar(null, null, EstadoVenta.ANULADA)).extracting(VentaDto::getId)
                .contains(ventaId);
        VentaDto leida = ventaService.obtener(ventaId);
        assertThat(leida.getEstadoVenta()).isEqualTo(EstadoVenta.ANULADA);
        assertThat(leida.getUsuarioAnulacionNombreCompleto()).isEqualTo("Ana Pérez");
    }

    @Test
    @DisplayName("Anular una venta que no fue en efectivo devuelve el stock sin mover la caja")
    void anularVentaConTarjeta() {
        Long cajaId = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long ventaId = ventaService.registrar(venta(FormaPago.TARJETA_DEBITO, renglon(gaseosa, "3"))).getId();

        ventaService.anular(ventaId, "Cliente arrepentido");

        assertThat(stockDe(gaseosa)).isEqualByComparingTo("10");
        assertThat(movimientosDeCaja(cajaId)).isEmpty();
        assertThat(estadoDe(ventaId)).isEqualTo(EstadoVenta.ANULADA);
    }

    @Test
    @DisplayName("Se puede anular una venta pendiente en cuenta corriente")
    void anularVentaPendiente() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        VentaDto cc = venta(FormaPago.CUENTA_CORRIENTE, renglon(gaseosa, "1"));
        cc.setClienteId(crearCliente(EstadoRegistro.ACTIVO));
        Long ventaId = ventaService.registrar(cc).getId();

        ventaService.anular(ventaId, "Venta duplicada");

        assertThat(estadoDe(ventaId)).isEqualTo(EstadoVenta.ANULADA);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("No se puede anular dos veces la misma venta")
    void anularDosVeces() {
        Long cajaId = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long ventaId = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))).getId();
        ventaService.anular(ventaId, "Error");

        assertThatThrownBy(() -> ventaService.anular(ventaId, "Otra vez"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ya está anulada");

        assertThat(stockDe(gaseosa)).isEqualByComparingTo("10");
        assertThat(movimientosDeCaja(cajaId)).hasSize(2);
    }

    @Test
    @DisplayName("Anular exige un motivo")
    void anularSinMotivo() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long ventaId = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))).getId();

        assertThatThrownBy(() -> ventaService.anular(ventaId, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> ventaService.anular(ventaId, "x".repeat(256)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("255");

        assertThat(estadoDe(ventaId)).isEqualTo(EstadoVenta.PAGADA);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("9");
    }

    @Test
    @DisplayName("Anular exige VENTA_ANULAR")
    void anularSinPermiso() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long ventaId = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))).getId();

        EnumSet<PermisoCodigo> sinAnular = EnumSet.allOf(PermisoCodigo.class);
        sinAnular.remove(PermisoCodigo.VENTA_ANULAR);
        sesionUsuario.iniciarSesion(usuario, sinAnular);

        assertThatThrownBy(() -> ventaService.anular(ventaId, "Error"))
                .isInstanceOf(AuthorizationException.class);
        assertThat(estadoDe(ventaId)).isEqualTo(EstadoVenta.PAGADA);
    }

    @Test
    @DisplayName("Si la caja de la venta ya se cerró, el egreso va a la caja abierta del usuario")
    void anularConCajaDeLaVentaCerrada() {
        Long cajaVenta = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "800.00", "10", UnidadMedida.UNIDAD, true);
        Long ventaId = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))).getId();
        cerrarCaja(cajaVenta);
        Long cajaNueva = abrirCaja();

        ventaService.anular(ventaId, "Devolución");

        assertThat(movimientosDeCaja(cajaVenta)).extracting(MovimientoCaja::getTipo)
                .containsExactly(TipoMovimientoCaja.VENTA);
        assertThat(movimientosDeCaja(cajaNueva)).extracting(MovimientoCaja::getTipo)
                .containsExactly(TipoMovimientoCaja.ANULACION_VENTA);
    }

    @Test
    @DisplayName("Una venta en efectivo sin caja abierta donde devolver el dinero no se anula y no toca el stock")
    void anularEnEfectivoSinCajaAbierta() {
        Long cajaVenta = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long ventaId = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))).getId();
        cerrarCaja(cajaVenta);

        assertThatThrownBy(() -> ventaService.anular(ventaId, "Devolución"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Abra una caja");

        assertThat(estadoDe(ventaId)).isEqualTo(EstadoVenta.PAGADA);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("9");
        assertThat(movimientoInventarioRepository.existsByOrigenAndOrigenIdAndMovimientoRevertidoIsNotNull(
                OrigenMovimientoInventario.VENTA, ventaId)).isFalse();
    }

    @Test
    @DisplayName("Si el efectivo de la caja no alcanza para devolver la venta, no se anula nada")
    void anularSinEfectivoSuficiente() {
        Long cajaId = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long ventaId = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))).getId();
        retirar(cajaId, "2000.00"); // 1000 + 1500 − 2000 = 500

        assertThatThrownBy(() -> ventaService.anular(ventaId, "Devolución"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("efectivo suficiente");

        assertThat(estadoDe(ventaId)).isEqualTo(EstadoVenta.PAGADA);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("9");
        assertThat(movimientosDeCaja(cajaId)).hasSize(2);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("listar() devuelve las ventas del período de la más reciente a la más antigua")
    void listar() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long primera = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))).getId();
        Long segunda = ventaService.registrar(venta(FormaPago.TRANSFERENCIA, renglon(gaseosa, "2"))).getId();

        List<VentaDto> hoy = ventaService.listar(LocalDate.now(), LocalDate.now(), null);

        assertThat(hoy).extracting(VentaDto::getId).containsSubsequence(segunda, primera);
        assertThat(ventaService.listar(LocalDate.now().plusDays(1), null, null)).isEmpty();
        assertThatThrownBy(() -> ventaService.listar(LocalDate.now(), LocalDate.now().minusDays(1), null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("listar() no incluye en un día la venta registrada a las 00:00 del día siguiente")
    void listarMedianoche() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long ventaId = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))).getId();

        // Un día propio del test, para no ver ventas de otros tests.
        LocalDate dia = LocalDate.of(1999, 1, 1);
        transaccion.executeWithoutResult(status ->
                entityManager.find(Venta.class, ventaId).setFecha(dia.plusDays(1).atStartOfDay()));

        assertThat(ventaService.listar(dia, dia, null)).extracting(VentaDto::getId).doesNotContain(ventaId);
        assertThat(ventaService.listar(dia.plusDays(1), dia.plusDays(1), null))
                .extracting(VentaDto::getId).containsExactly(ventaId);
    }

    @Test
    @DisplayName("listar() filtra por estado")
    void listarPorEstado() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long clienteId = crearCliente(EstadoRegistro.ACTIVO);
        Long pagada = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))).getId();
        VentaDto cc = venta(FormaPago.CUENTA_CORRIENTE, renglon(gaseosa, "1"));
        cc.setClienteId(clienteId);
        Long pendiente = ventaService.registrar(cc).getId();

        assertThat(ventaService.listar(null, null, EstadoVenta.PENDIENTE)).extracting(VentaDto::getId)
                .contains(pendiente).doesNotContain(pagada);
        assertThat(ventaService.listar(null, null, EstadoVenta.PAGADA)).extracting(VentaDto::getId)
                .contains(pagada).doesNotContain(pendiente);
    }

    /** Registra hoy dos ventas válidas (1500 y 3000) y una anulada (4500) en una caja nueva. */
    private Long cajaConVentasDeHoy() {
        Long cajaId = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1")));
        ventaService.registrar(venta(FormaPago.TRANSFERENCIA, renglon(gaseosa, "2")));
        Long anulada = ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "3"))).getId();
        ventaService.anular(anulada, "Error en la carga");
        return cajaId;
    }

    @Test
    @DisplayName("ventasDelDiaPorCaja() totaliza las ventas de hoy de cada caja, sin las anuladas")
    void ventasDelDiaPorCaja() {
        Long cajaId = cajaConVentasDeHoy();

        // La base es compartida con otros tests: puede haber cajas de otros usuarios.
        List<VentasPorCaja> cajas = ventaService.ventasDelDiaPorCaja();
        VentasPorCaja caja = cajas.stream().filter(c -> c.cajaId().equals(cajaId)).findFirst().orElseThrow();

        assertThat(caja.usuarioNombreCompleto()).isEqualTo("Ana Pérez");
        assertThat(caja.estadoCaja()).isEqualTo(EstadoCaja.ABIERTA);
        assertThat(caja.cantidad()).isEqualTo(2);
        assertThat(caja.total()).isEqualByComparingTo("4500");

        sesionUsuario.iniciarSesion(usuario, EnumSet.of(PermisoCodigo.VENTA_CREAR));
        assertThatThrownBy(() -> ventaService.ventasDelDiaPorCaja()).isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("ventasDelDiaPorCaja() incluye las cajas abiertas aunque hoy no hayan vendido")
    void ventasDelDiaPorCajaIncluyeAbiertasSinVentas() {
        Long cajaId = abrirCaja();

        VentasPorCaja caja = ventaService.ventasDelDiaPorCaja().stream()
                .filter(c -> c.cajaId().equals(cajaId)).findFirst().orElseThrow();

        assertThat(caja.usuarioNombreCompleto()).isEqualTo("Ana Pérez");
        assertThat(caja.estadoCaja()).isEqualTo(EstadoCaja.ABIERTA);
        assertThat(caja.cantidad()).isZero();
        assertThat(caja.total()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("ventasDelDiaPropias() suma sólo lo vendido hoy en las cajas del usuario")
    void ventasDelDiaPropias() {
        cajaConVentasDeHoy();

        // Otro usuario con ventas hoy: no tiene que sumar.
        Usuario otro = transaccion.execute(status -> {
            Usuario u = Usuario.builder()
                    .usuario("otro" + sufijo).password("x").nombre("Luis").apellido("Díaz")
                    .empresa(entityManager.merge(usuario).getEmpresa()).rol(entityManager.merge(usuario).getRol())
                    .tipoDocumento(TipoDocumento.DNI).documento("9" + sufijo)
                    .build();
            entityManager.persist(u);
            return u;
        });
        Usuario ana = usuario;
        usuario = otro;
        sesionUsuario.iniciarSesion(otro, EnumSet.allOf(PermisoCodigo.class));
        abrirCaja();
        Long queso = crearProducto("Queso", "8000.00", "5", UnidadMedida.UNIDAD, true);
        ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(queso, "1")));

        sesionUsuario.iniciarSesion(ana, EnumSet.of(PermisoCodigo.VENTA_CREAR));
        ResumenVentas propias = ventaService.ventasDelDiaPropias();

        assertThat(propias.cantidad()).isEqualTo(2);
        assertThat(propias.total()).isEqualByComparingTo("4500");

        sesionUsuario.iniciarSesion(ana, EnumSet.of(PermisoCodigo.VENTA_VER));
        assertThatThrownBy(() -> ventaService.ventasDelDiaPropias()).isInstanceOf(AuthorizationException.class);
    }

    // ------------------------------------------------------------------
    // Control del efectivo en caja
    // ------------------------------------------------------------------

    /**
     * Configura el control del efectivo en la empresa que usa el sistema (la
     * primera); {@link #quitarControlEfectivo} lo deja sin control.
     */
    private void controlEfectivo(String montoMaximo, Integer clientesAntesDeBloqueo) {
        transaccion.executeWithoutResult(status -> entityManager.createNativeQuery(
                        "update empresas set monto_maximo_caja = " + montoMaximo
                                + ", clientes_antes_de_bloqueo = " + clientesAntesDeBloqueo
                                + " where id = (select min(id) from empresas)")
                .executeUpdate());
    }

    @AfterEach
    void quitarControlEfectivo() {
        controlEfectivo(null, null);
    }

    @Test
    @DisplayName("Sin monto máximo no hay control del efectivo: se vende como siempre")
    void sinControlDeEfectivo() {
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "50000.00", "10", UnidadMedida.UNIDAD, true);

        ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1")));
        ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1")));

        assertThat(ventaService.estadoEfectivoCaja()).isEmpty();
    }

    @Test
    @DisplayName("Al alcanzar el monto se avisa; superados los clientes permitidos se bloquea y un retiro desbloquea")
    void avisoBloqueoYDesbloqueo() {
        controlEfectivo("2000", 1);
        Long cajaId = abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);

        // Saldo 1000: todavía no alcanza el monto.
        assertThat(ventaService.estadoEfectivoCaja()).hasValueSatisfying(estado -> {
            assertThat(estado.montoAlcanzado()).isFalse();
            assertThat(estado.bloqueada()).isFalse();
        });

        // 1000 + 1500 = 2500: alcanza el monto con esta venta, que no cuenta para el bloqueo.
        ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1")));

        assertThat(ventaService.estadoEfectivoCaja()).hasValueSatisfying(estado -> {
            assertThat(estado.montoAlcanzado()).isTrue();
            assertThat(estado.saldo()).isEqualByComparingTo("2500");
            assertThat(estado.clientesRestantes()).isEqualTo(1);
            assertThat(estado.bloqueada()).isFalse();
            assertThat(estado.mensaje()).contains("1 cliente más");
        });

        // Las ventas que no son en efectivo también cuentan.
        ventaService.registrar(venta(FormaPago.TARJETA_DEBITO, renglon(gaseosa, "1")));

        assertThat(ventaService.estadoEfectivoCaja()).hasValueSatisfying(estado -> {
            assertThat(estado.clientesRestantes()).isZero();
            assertThat(estado.bloqueada()).isTrue();
        });

        long ventasAntes = ventaRepository.count();

        assertThatThrownBy(() -> ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("bloqueada");

        assertThat(ventaRepository.count()).isEqualTo(ventasAntes);
        assertThat(stockDe(gaseosa)).isEqualByComparingTo("8");

        // Un retiro que baja el saldo del monto (2500 − 600 = 1900) desbloquea la caja.
        retirar(cajaId, "600");

        assertThat(ventaService.estadoEfectivoCaja()).hasValueSatisfying(estado -> {
            assertThat(estado.montoAlcanzado()).isFalse();
            assertThat(estado.bloqueada()).isFalse();
        });

        // Si vuelve a alcanzar el monto, la cuenta de clientes empieza de nuevo.
        ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1")));

        assertThat(ventaService.estadoEfectivoCaja()).hasValueSatisfying(estado -> {
            assertThat(estado.montoAlcanzado()).isTrue();
            assertThat(estado.clientesRestantes()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("Con cero clientes la caja se bloquea apenas alcanza el monto")
    void bloqueoInmediato() {
        controlEfectivo("2000", 0);
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);

        ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1")));

        assertThat(ventaService.estadoEfectivoCaja()).hasValueSatisfying(estado ->
                assertThat(estado.bloqueada()).isTrue());
        assertThatThrownBy(() -> ventaService.registrar(venta(FormaPago.TARJETA_DEBITO, renglon(gaseosa, "1"))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Sin cantidad de clientes se avisa pero nunca se bloquea")
    void avisoSinBloqueo() {
        controlEfectivo("2000", null);
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);

        for (int i = 0; i < 4; i++) {
            ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1")));
        }

        assertThat(ventaService.estadoEfectivoCaja()).hasValueSatisfying(estado -> {
            assertThat(estado.montoAlcanzado()).isTrue();
            assertThat(estado.bloqueada()).isFalse();
            assertThat(estado.clientesRestantes()).isNull();
            assertThat(estado.mensaje()).contains("Debe retirar dinero");
        });
    }

    @Test
    @DisplayName("Las ventas anuladas no cuentan como clientes atendidos")
    void anuladasNoCuentan() {
        controlEfectivo("2000", 1);
        abrirCaja();
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);

        ventaService.registrar(venta(FormaPago.EFECTIVO, renglon(gaseosa, "1")));
        Long conTarjeta = ventaService.registrar(venta(FormaPago.TARJETA_DEBITO, renglon(gaseosa, "1"))).getId();

        assertThat(ventaService.estadoEfectivoCaja()).hasValueSatisfying(estado ->
                assertThat(estado.bloqueada()).isTrue());

        ventaService.anular(conTarjeta, "Error en la carga");

        assertThat(ventaService.estadoEfectivoCaja()).hasValueSatisfying(estado -> {
            assertThat(estado.bloqueada()).isFalse();
            assertThat(estado.clientesRestantes()).isEqualTo(1);
        });
    }

    // ------------------------------------------------------------------
    // Consultas del punto de venta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("tieneCajaAbierta() indica si el usuario puede vender")
    void tieneCajaAbierta() {
        assertThat(ventaService.tieneCajaAbierta()).isFalse();

        abrirCaja();

        assertThat(ventaService.tieneCajaAbierta()).isTrue();
    }

    @Test
    @DisplayName("buscarProductoPorCodigo() encuentra por código de barras o código interno, sólo activos")
    void buscarPorCodigo() {
        Long id = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        transaccion.executeWithoutResult(status ->
                entityManager.find(Producto.class, id).setCodigoBarras("779" + sufijo));

        assertThat(ventaService.buscarProductoPorCodigo("779" + sufijo)).map(ProductoDto::getId).hasValue(id);
        assertThat(ventaService.buscarProductoPorCodigo(("gaseosa" + sufijo).toUpperCase()))
                .map(ProductoDto::getId).hasValue(id);
        assertThat(ventaService.buscarProductoPorCodigo("no-existe")).isEmpty();

        transaccion.executeWithoutResult(status ->
                entityManager.find(Producto.class, id).setEstadoRegistro(EstadoRegistro.INACTIVO));

        assertThat(ventaService.buscarProductoPorCodigo("779" + sufijo)).isEmpty();
        assertThat(ventaService.buscarProductos("Gaseosa " + sufijo)).isEmpty();
    }

    @Test
    @DisplayName("buscarProductos() y listarClientes() sólo exigen VENTA_CREAR")
    void consultasConSoloVentaCrear() {
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "10", UnidadMedida.UNIDAD, true);
        Long clienteId = crearCliente(EstadoRegistro.ACTIVO);
        sesionUsuario.iniciarSesion(usuario, EnumSet.of(PermisoCodigo.VENTA_CREAR));

        assertThat(ventaService.buscarProductos("Gaseosa " + sufijo)).extracting(ProductoDto::getId)
                .containsExactly(gaseosa);
        assertThat(ventaService.listarClientes()).extracting(ClienteDto::getId).contains(clienteId);

        sesionUsuario.iniciarSesion(usuario, EnumSet.of(PermisoCodigo.VENTA_VER));

        assertThatThrownBy(() -> ventaService.buscarProductos("")).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> ventaService.listarClientes()).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> ventaService.tieneCajaAbierta()).isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("verificarStock() suma renglones del mismo producto e informa todos los faltantes")
    void verificarStock() {
        Long gaseosa = crearProducto("Gaseosa", "1500.00", "3", UnidadMedida.UNIDAD, true);
        Long queso = crearProducto("Queso", "8000.00", "0.5", UnidadMedida.KILOGRAMO, true);
        Long servicio = crearProducto("Servicio", "500.00", "0", UnidadMedida.UNIDAD, false);

        assertThat(ventaService.verificarStock(List.of(renglon(gaseosa, "3"), renglon(servicio, "10")))).isEmpty();

        List<String> faltantes = ventaService.verificarStock(List.of(
                renglon(gaseosa, "2"), renglon(gaseosa, "2"), renglon(queso, "1"), renglon(servicio, "5")));

        assertThat(faltantes).hasSize(2);
        assertThat(faltantes.get(0)).contains("Gaseosa " + sufijo).contains("disponible 3").contains("solicitado 4");
        assertThat(faltantes.get(1)).contains("Queso " + sufijo);
    }

}
