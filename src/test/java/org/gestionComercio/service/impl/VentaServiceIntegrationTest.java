package org.gestionComercio.service.impl;

import jakarta.persistence.EntityManager;
import org.gestionComercio.config.AuditorProvider;
import org.gestionComercio.config.JpaAuditingConfig;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
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
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.DetalleVentaMapperImpl;
import org.gestionComercio.mapper.MovimientoInventarioMapperImpl;
import org.gestionComercio.mapper.VentaMapperImpl;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.MovimientoInventarioRepository;
import org.gestionComercio.repository.VentaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.InventarioService;
import org.gestionComercio.service.VentaService;
import org.gestionComercio.validation.VentaValidator;
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
 * Prueba el registro de ventas contra una base SQLite real para verificar que
 * venta, stock y caja se confirmen o se reviertan juntos.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/ventas-it.db",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        VentaServiceImpl.class,
        VentaValidator.class,
        VentaMapperImpl.class,
        DetalleVentaMapperImpl.class,
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
        assertThat(movimientos.get(0).getDescripcion()).isEqualTo("Venta N° " + guardada.getId());
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
        assertThatThrownBy(() -> ventaService.listar(null, null)).isInstanceOf(AuthorizationException.class);
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

        List<VentaDto> hoy = ventaService.listar(LocalDate.now(), LocalDate.now());

        assertThat(hoy).extracting(VentaDto::getId).containsSubsequence(segunda, primera);
        assertThat(ventaService.listar(LocalDate.now().plusDays(1), null)).isEmpty();
        assertThatThrownBy(() -> ventaService.listar(LocalDate.now(), LocalDate.now().minusDays(1)))
                .isInstanceOf(BusinessException.class);
    }

}
