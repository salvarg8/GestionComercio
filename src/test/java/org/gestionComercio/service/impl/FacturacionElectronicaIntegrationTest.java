package org.gestionComercio.service.impl;

import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.gestionComercio.arca.AlmacenClaveEnMemoria;
import org.gestionComercio.arca.ArcaConfig;
import org.gestionComercio.arca.ClaveCertificadoArca;
import org.gestionComercio.arca.ArcaSimulado;
import org.gestionComercio.arca.CertificadosDePrueba;
import org.gestionComercio.arca.Wsfe;
import org.gestionComercio.config.AuditorProvider;
import org.gestionComercio.config.JpaAuditingConfig;
import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.dto.arca.ConfiguracionArcaDto;
import org.gestionComercio.dto.arca.PruebaConexionArca;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.AmbienteArca;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.EstadoComprobanteArca;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.mapper.ClienteMapperImpl;
import org.gestionComercio.mapper.ComprobanteArcaMapperImpl;
import org.gestionComercio.mapper.ConfiguracionMapperImpl;
import org.gestionComercio.mapper.DetalleVentaMapperImpl;
import org.gestionComercio.mapper.EmpresaMapperImpl;
import org.gestionComercio.mapper.MovimientoInventarioMapperImpl;
import org.gestionComercio.mapper.ProductoMapperImpl;
import org.gestionComercio.mapper.VentaMapperImpl;
import org.gestionComercio.repository.BloqueoArcaRepository;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.NumeradorComprobanteRepository;
import org.gestionComercio.repository.TicketAccesoArcaRepository;
import org.gestionComercio.repository.VentaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ComprobanteVentaService;
import org.gestionComercio.service.ConfiguracionArcaService;
import org.gestionComercio.service.FacturacionElectronicaService;
import org.gestionComercio.service.InventarioService;
import org.gestionComercio.service.VentaService;
import org.gestionComercio.licencia.ModulosTodosHabilitados;
import org.gestionComercio.service.impl.arca.ConfiguracionArcaVigente;
import org.gestionComercio.service.impl.arca.EmisionComprobantesArca;
import org.gestionComercio.service.impl.arca.TicketAccesoService;
import org.gestionComercio.support.PostgresTestcontainersConfig;
import org.gestionComercio.validation.VentaValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
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
 * Facturación electrónica de punta a punta contra un PostgreSQL real y un
 * ARCA simulado ({@link ArcaSimulado}): registro de la venta, autorización,
 * rechazos, cortes de conexión, numeración con varias cajas a la vez y notas
 * de crédito al anular.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false",
        "arca.clave-certificado=clave-de-prueba",
        // Cada autorización en curso usa dos conexiones (bloqueo + escrituras).
        "spring.datasource.hikari.maximum-pool-size=12"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        PostgresTestcontainersConfig.class,
        FacturacionElectronicaIntegrationTest.ArcaDePrueba.class,
        ArcaConfig.class,
        VentaServiceImpl.class,
        ControlEfectivoCaja.class,
        ConfiguracionServiceImpl.class,
        ConfiguracionMapperImpl.class,
        NumeradorComprobanteRepository.class,
        ComprobanteVentaServiceImpl.class,
        FacturacionElectronicaServiceImpl.class,
        ConfiguracionArcaServiceImpl.class,
        ConfiguracionArcaVigente.class,
        ModulosTodosHabilitados.class,
        ClaveCertificadoArca.class,
        AlmacenClaveEnMemoria.class,
        EmisionComprobantesArca.class,
        TicketAccesoService.class,
        BloqueoArcaRepository.class,
        ComprobanteArcaMapperImpl.class,
        EmpresaMapperImpl.class,
        VentaValidator.class,
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
class FacturacionElectronicaIntegrationTest {

    private static final String CUIT_EMPRESA = "30712345671";
    private static final int PUNTO_VENTA = 3;
    private static final AtomicLong SECUENCIA = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    @TestConfiguration(proxyBeanMethods = false)
    static class ArcaDePrueba {

        @Bean
        ArcaSimulado arcaSimulado() {
            return new ArcaSimulado();
        }
    }

    @Autowired private VentaService ventaService;
    @Autowired private FacturacionElectronicaService facturacion;
    @Autowired private ConfiguracionArcaService configuracionArca;
    @Autowired private ComprobanteVentaService comprobanteVentaService;
    @Autowired private InventarioService inventarioService;
    @Autowired private VentaRepository ventaRepository;
    @Autowired private EmpresaRepository empresaRepository;
    @Autowired private TicketAccesoArcaRepository ticketRepository;
    @Autowired private SesionUsuario sesionUsuario;
    @Autowired private ArcaSimulado arca;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;

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
        arca.proximo(ArcaSimulado.Proximo.NORMAL);
        arca.sinConexion(false);
        sufijo = String.valueOf(SECUENCIA.incrementAndGet());

        usuario = transaccion.execute(status -> {

            Empresa empresa = empresaRepository.findByCuit(CUIT_EMPRESA).orElseGet(() -> {
                Empresa e = Empresa.builder()
                        .razonSocial("El Centro SRL").cuit(CUIT_EMPRESA)
                        .condicionIva(CondicionIVA.RESPONSABLE_INSCRIPTO)
                        .fechaInicioActividades(LocalDate.of(2015, 3, 1))
                        .build();
                entityManager.persist(e);
                return e;
            });

            Rol rol = Rol.builder().nombre("ROL" + sufijo).build();
            Usuario u = Usuario.builder()
                    .usuario("user" + sufijo).password("x").nombre("Ana").apellido("Pérez")
                    .empresa(empresa).rol(rol).tipoDocumento(TipoDocumento.DNI).documento(sufijo)
                    .build();
            categoria = Categoria.builder().nombre("Categoria " + sufijo).build();
            marca = Marca.builder().nombre("Marca " + sufijo).build();
            proveedor = Proveedor.builder().razonSocial("Proveedor " + sufijo).build();
            entityManager.persist(rol);
            entityManager.persist(u);
            entityManager.persist(categoria);
            entityManager.persist(marca);
            entityManager.persist(proveedor);

            Caja caja = Caja.builder()
                    .fechaApertura(LocalDateTime.now())
                    .saldoInicial(new BigDecimal("100000.00"))
                    .estadoCaja(EstadoCaja.ABIERTA)
                    .usuario(u)
                    .usuarioApertura(u)
                    .build();
            entityManager.persist(caja);
            return u;
        });

        sesionUsuario.iniciarSesion(usuario, EnumSet.allOf(PermisoCodigo.class));
        configurarArca();
    }

    /** Pedido de certificado, certificado "firmado por ARCA", punto de venta y habilitación. */
    private void configurarArca() {

        ConfiguracionArcaDto actual = configuracionArca.obtener();

        if (actual.isHabilitada()) {
            return;
        }

        String csr = configuracionArca.generarPedidoCertificado();
        byte[] certificado = encoded(CertificadosDePrueba.firmar(csr, Duration.ofDays(730)));
        configuracionArca.importarCertificado(certificado);

        configuracionArca.guardar(ConfiguracionArcaDto.builder()
                .ambiente(AmbienteArca.HOMOLOGACION)
                .puntoVenta(PUNTO_VENTA)
                .habilitada(true)
                .build());
    }

    private static byte[] encoded(java.security.cert.X509Certificate certificado) {
        try {
            return certificado.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Long crearProducto(String precio) {
        Long id = transaccion.execute(status -> {
            Producto producto = Producto.builder()
                    .codigo("P" + sufijo + SECUENCIA.incrementAndGet()).nombre("Producto " + sufijo)
                    .unidadMedida(UnidadMedida.UNIDAD)
                    .precioVenta(new BigDecimal(precio))
                    .manejaStock(true)
                    .categoria(entityManager.merge(categoria))
                    .marca(entityManager.merge(marca))
                    .proveedor(entityManager.merge(proveedor))
                    .build();
            entityManager.persist(producto);
            return producto.getId();
        });
        inventarioService.registrarEntrada(id, new BigDecimal("1000"), "Stock inicial");
        return id;
    }

    private Long crearCliente(CondicionIVA condicion, TipoDocumento tipo) {
        return transaccion.execute(status -> {
            Cliente cliente = Cliente.builder()
                    .nombre("Cliente").apellido(sufijo)
                    .tipoDocumento(tipo)
                    .numeroDocumento(tipo == TipoDocumento.CUIT ? "20" + String.format("%09d", Long.parseLong(sufijo))
                            : String.format("%08d", Long.parseLong(sufijo)))
                    .condicionIva(condicion)
                    .build();
            entityManager.persist(cliente);
            return cliente.getId();
        });
    }

    private VentaDto registrar(TipoComprobante tipo, Long clienteId, Long productoId, String cantidad) {
        DetalleVentaDto renglon = new DetalleVentaDto();
        renglon.setProductoId(productoId);
        renglon.setCantidad(new BigDecimal(cantidad));

        VentaDto dto = VentaDto.builder()
                .tipoComprobante(tipo)
                .clienteId(clienteId)
                .formaPago(FormaPago.EFECTIVO)
                .detalles(new ArrayList<>(List.of(renglon)))
                .build();

        return ventaService.registrar(dto);
    }

    private VentaDto facturaB() {
        return registrar(TipoComprobante.FACTURA_B, null, crearProducto("1210.00"), "1");
    }

    private static ComprobanteArcaDto unico(List<ComprobanteArcaDto> comprobantes) {
        assertThat(comprobantes).hasSize(1);
        return comprobantes.get(0);
    }

    private String textoPdf(Long ventaId) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(comprobanteVentaService.generarPdf(ventaId))) {
            return new PDFTextStripper().getText(pdf);
        }
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("Factura B a consumidor final: queda pendiente al registrarla y ARCA le da número y CAE")
    void facturaBConsumidorFinal() throws Exception {
        long ultimo = arca.ultimo(PUNTO_VENTA, 6);

        VentaDto venta = facturaB();

        assertThat(venta.getNumeroComprobante()).isNull();
        assertThat(venta.getFactura().getEstado()).isEqualTo(EstadoComprobanteArca.PENDIENTE);
        assertThat(venta.getComprobante()).isEqualTo("Factura B (pendiente de CAE)");

        ComprobanteArcaDto factura = unico(facturacion.autorizar(venta.getId()));

        assertThat(factura.getEstado()).isEqualTo(EstadoComprobanteArca.AUTORIZADO);
        assertThat(factura.getNumero()).isEqualTo(ultimo + 1);
        assertThat(factura.getPuntoVenta()).isEqualTo(PUNTO_VENTA);
        assertThat(factura.getCae()).isNotBlank();
        assertThat(factura.getVencimientoCae()).isNotNull();
        assertThat(factura.getImporteNeto()).isEqualByComparingTo("1000.00");
        assertThat(factura.getImporteIva()).isEqualByComparingTo("210.00");

        Wsfe.SolicitudCae enviada = arca.solicitudes().get(arca.solicitudes().size() - 1);
        assertThat(enviada.tipoComprobante()).isEqualTo(6);
        assertThat(enviada.docTipo()).isEqualTo(99);
        assertThat(enviada.condicionIvaReceptor()).isEqualTo(5);
        assertThat(enviada.importeTotal()).isEqualByComparingTo("1210.00");
        assertThat(enviada.alicuotas()).containsExactly(
                new Wsfe.AlicuotaIva(5, new BigDecimal("1000.00"), new BigDecimal("210.00")));

        VentaDto guardada = ventaService.obtener(venta.getId());
        assertThat(guardada.getComprobante())
                .isEqualTo(String.format("Factura B N° %04d-%08d", PUNTO_VENTA, ultimo + 1));
        assertThat(guardada.getFactura().getQrUrl()).startsWith("https://www.afip.gob.ar/fe/qr/?p=");

        assertThat(textoPdf(venta.getId()))
                .contains("Comprobante autorizado por ARCA")
                .contains(factura.getCae())
                .contains("COD. 06")
                .contains("IVA contenido")
                .contains("Inicio de actividades: 01/03/2015");
    }

    @Test
    @DisplayName("Factura A a un responsable inscripto con CUIT; la letra equivocada se rechaza al registrar")
    void facturaAResponsableInscripto() throws Exception {
        Long cliente = crearCliente(CondicionIVA.RESPONSABLE_INSCRIPTO, TipoDocumento.CUIT);
        Long producto = crearProducto("121.00");

        assertThat(facturacion.tipoFactura(cliente)).isEqualTo(TipoComprobante.FACTURA_A);
        assertThat(facturacion.tipoFactura(null)).isEqualTo(TipoComprobante.FACTURA_B);

        long ventasAntes = ventaRepository.count();
        assertThatThrownBy(() -> registrar(TipoComprobante.FACTURA_B, cliente, producto, "1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("factura a");
        assertThat(ventaRepository.count()).isEqualTo(ventasAntes);

        VentaDto venta = registrar(TipoComprobante.FACTURA_A, cliente, producto, "2");
        ComprobanteArcaDto factura = unico(facturacion.autorizar(venta.getId()));

        assertThat(factura.isAutorizado()).isTrue();
        assertThat(factura.getDocTipo()).isEqualTo(80);
        assertThat(factura.getCondicionIvaReceptor()).isEqualTo(1);

        assertThat(textoPdf(venta.getId())).contains("Neto gravado").contains("IVA 21%").contains("COD. 01");
    }

    @Test
    @DisplayName("El ticket de acceso se pide una vez y se reutiliza hasta que vence")
    void ticketReutilizado() {
        transaccion.executeWithoutResult(s -> ticketRepository.deleteAll());
        int loginsAntes = arca.logins();

        facturacion.autorizar(facturaB().getId());
        facturacion.autorizar(facturaB().getId());
        facturacion.autorizar(facturaB().getId());

        assertThat(arca.logins() - loginsAntes).isEqualTo(1);
    }

    @Test
    @DisplayName("Si ARCA rechaza, se guarda el motivo, no queda facturada y se puede reintentar")
    void rechazoYReintento() {
        VentaDto venta = facturaB();
        arca.proximo(ArcaSimulado.Proximo.RECHAZAR);

        ComprobanteArcaDto rechazada = unico(facturacion.autorizar(venta.getId()));

        assertThat(rechazada.getEstado()).isEqualTo(EstadoComprobanteArca.RECHAZADO);
        assertThat(rechazada.getNumero()).isNull();
        assertThat(rechazada.getCae()).isNull();
        assertThat(rechazada.getObservaciones()).contains("10242");
        assertThat(ventaService.obtener(venta.getId()).getFactura().isAutorizado()).isFalse();
        assertThat(facturacion.listarSinAutorizar()).extracting(ComprobanteArcaDto::getId)
                .contains(rechazada.getId());

        // Los rechazados no se reintentan solos, pero sí al pedirlo para la venta.
        facturacion.autorizarPendientes();
        assertThat(ventaService.obtener(venta.getId()).getFactura().getEstado())
                .isEqualTo(EstadoComprobanteArca.RECHAZADO);

        assertThat(unico(facturacion.autorizar(venta.getId())).isAutorizado()).isTrue();
    }

    @Test
    @DisplayName("Corte después de enviar: queda ENVIADO y el reintento confirma el CAE sin volver a pedirlo")
    void corteDespuesDeEnviar() {
        VentaDto venta = facturaB();
        arca.proximo(ArcaSimulado.Proximo.CORTAR_DESPUES_DE_AUTORIZAR);

        ComprobanteArcaDto enviada = unico(facturacion.autorizar(venta.getId()));

        assertThat(enviada.getEstado()).isEqualTo(EstadoComprobanteArca.ENVIADO);
        assertThat(enviada.getNumero()).isNotNull();
        assertThat(enviada.getObservaciones()).contains("No se obtuvo respuesta");

        int solicitudes = arca.solicitudes().size();

        ComprobanteArcaDto confirmada = unico(facturacion.autorizar(venta.getId()));

        assertThat(confirmada.getEstado()).isEqualTo(EstadoComprobanteArca.AUTORIZADO);
        assertThat(confirmada.getNumero()).isEqualTo(enviada.getNumero());
        assertThat(arca.solicitudes()).hasSize(solicitudes);
        assertThat(arca.rechazosPorNumero()).isZero();
    }

    @Test
    @DisplayName("Corte antes de que llegue: el reintento verifica que ARCA no lo tiene y lo vuelve a enviar")
    void corteAntesDeRecibir() {
        VentaDto venta = facturaB();
        arca.proximo(ArcaSimulado.Proximo.CORTAR_ANTES_DE_RECIBIR);

        ComprobanteArcaDto enviada = unico(facturacion.autorizar(venta.getId()));
        assertThat(enviada.getEstado()).isEqualTo(EstadoComprobanteArca.ENVIADO);

        // Otra venta del mismo tipo confirma primero la enviada (está en la misma numeración).
        VentaDto otra = facturaB();
        ComprobanteArcaDto otraFactura = unico(facturacion.autorizar(otra.getId()));
        ComprobanteArcaDto reenviada = unico(facturacion.autorizar(venta.getId()));

        assertThat(otraFactura.isAutorizado()).isTrue();
        assertThat(reenviada.isAutorizado()).isTrue();
        assertThat(reenviada.getNumero()).isNotEqualTo(otraFactura.getNumero());
        assertThat(arca.rechazosPorNumero()).isZero();
    }

    @Test
    @DisplayName("Si ARCA no responde antes de enviar, la venta queda registrada y la factura pendiente")
    void sinConexion() {
        VentaDto venta = facturaB();
        arca.sinConexion(true);

        assertThatThrownBy(() -> facturacion.autorizar(venta.getId())).isInstanceOf(BusinessException.class);

        VentaDto guardada = ventaService.obtener(venta.getId());
        assertThat(guardada.getEstadoVenta()).isEqualTo(EstadoVenta.PAGADA);
        assertThat(guardada.getFactura().getEstado()).isEqualTo(EstadoComprobanteArca.PENDIENTE);

        arca.sinConexion(false);
        assertThat(facturacion.autorizarPendientes()).extracting(ComprobanteArcaDto::getEstado)
                .containsOnly(EstadoComprobanteArca.AUTORIZADO);
    }

    @Test
    @DisplayName("Varias cajas autorizando a la vez obtienen números distintos y consecutivos")
    void numeracionConcurrente() throws Exception {
        int cantidad = 6;
        List<Long> ventas = new ArrayList<>();

        for (int i = 0; i < cantidad; i++) {
            ventas.add(facturaB().getId());
        }

        long ultimo = arca.ultimo(PUNTO_VENTA, 6);
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService ejecutor = Executors.newFixedThreadPool(cantidad);
        List<Future<ComprobanteArcaDto>> resultados = new ArrayList<>();

        try {
            for (Long ventaId : ventas) {
                resultados.add(ejecutor.submit(() -> {
                    largada.await();
                    return unico(facturacion.autorizar(ventaId));
                }));
            }

            largada.countDown();

            List<Long> numeros = new ArrayList<>();
            for (Future<ComprobanteArcaDto> resultado : resultados) {
                ComprobanteArcaDto factura = resultado.get(60, TimeUnit.SECONDS);
                assertThat(factura.isAutorizado()).isTrue();
                numeros.add(factura.getNumero());
            }

            assertThat(numeros).containsExactlyInAnyOrderElementsOf(
                    java.util.stream.LongStream.rangeClosed(ultimo + 1, ultimo + cantidad).boxed().toList());
            assertThat(arca.rechazosPorNumero()).isZero();
        } finally {
            ejecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Anular una venta con factura autorizada emite la nota de crédito asociada")
    void anulacionConNotaDeCredito() throws Exception {
        VentaDto venta = facturaB();
        ComprobanteArcaDto factura = unico(facturacion.autorizar(venta.getId()));

        VentaDto anulada = ventaService.anular(venta.getId(), "Devolución del cliente");

        assertThat(anulada.getNotaCredito()).isNotNull();
        assertThat(anulada.getNotaCredito().getEstado()).isEqualTo(EstadoComprobanteArca.PENDIENTE);
        assertThat(anulada.getNotaCredito().getTipoComprobante()).isEqualTo(TipoComprobante.NOTA_CREDITO_B);

        List<ComprobanteArcaDto> comprobantes = facturacion.autorizar(venta.getId());

        assertThat(comprobantes).hasSize(2);
        ComprobanteArcaDto nota = comprobantes.get(1);
        assertThat(nota.isAutorizado()).isTrue();
        assertThat(nota.getImporteTotal()).isEqualByComparingTo(factura.getImporteTotal());
        assertThat(nota.getComprobanteAsociado()).isEqualTo(factura.getComprobante());

        Wsfe.SolicitudCae enviada = arca.solicitudes().get(arca.solicitudes().size() - 1);
        assertThat(enviada.tipoComprobante()).isEqualTo(8);
        assertThat(enviada.asociado()).isEqualTo(new Wsfe.ComprobanteAsociado(6, PUNTO_VENTA, factura.getNumero(),
                Long.parseLong(CUIT_EMPRESA), factura.getFecha()));

        assertThat(textoPdf(venta.getId())).contains("Anulada con Nota de crédito B");
    }

    @Test
    @DisplayName("No se anula una venta cuya factura se envió sin respuesta; sí, cancelando la factura, si no se envió")
    void anulacionSegunEstadoDeLaFactura() {
        VentaDto enviada = facturaB();
        arca.proximo(ArcaSimulado.Proximo.CORTAR_DESPUES_DE_AUTORIZAR);
        facturacion.autorizar(enviada.getId());

        assertThatThrownBy(() -> ventaService.anular(enviada.getId(), "Error"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reintente la autorización");
        assertThat(ventaService.obtener(enviada.getId()).getEstadoVenta()).isNotEqualTo(EstadoVenta.ANULADA);

        VentaDto pendiente = facturaB();
        VentaDto anulada = ventaService.anular(pendiente.getId(), "Error de carga");

        assertThat(anulada.getFactura().getEstado()).isEqualTo(EstadoComprobanteArca.CANCELADO);
        assertThat(anulada.getNotaCredito()).isNull();

        int solicitudes = arca.solicitudes().size();
        facturacion.autorizar(pendiente.getId());
        assertThat(arca.solicitudes()).hasSize(solicitudes);

        // Se confirma la que quedó enviada para no dejarla colgada para los otros tests.
        facturacion.autorizar(enviada.getId());
    }

    @Test
    @DisplayName("Un certificado que no corresponde al pedido generado se rechaza")
    void certificadoDeOtroPedido() {
        configuracionArca.generarPedidoCertificado();
        byte[] deOtro = encoded(CertificadosDePrueba.credencial(CUIT_EMPRESA).certificado());

        assertThatThrownBy(() -> configuracionArca.importarCertificado(deOtro))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no corresponde");

        ConfiguracionArcaDto config = configuracionArca.obtener();
        assertThat(config.isPedidoPendiente()).isTrue();
        assertThat(config.isHabilitada()).isTrue();
        assertThat(config.getCertificadoTitular()).contains("CUIT " + CUIT_EMPRESA);
    }

    @Test
    @DisplayName("La prueba de conexión verifica servidores, certificado y punto de venta")
    void probarConexion() {
        PruebaConexionArca prueba = configuracionArca.probarConexion();

        assertThat(prueba.exitosa()).isTrue();
        assertThat(prueba.pasos()).extracting(PruebaConexionArca.Paso::nombre)
                .containsExactly("Configuración", "Conexión con ARCA", "Certificado", "Punto de venta " + PUNTO_VENTA);
        assertThat(prueba.pasos().get(3).detalle()).contains("Factura A").contains("Factura B");
    }

    @Test
    @DisplayName("Sin conexión, la prueba de conexión lo informa en el paso correspondiente y no verifica el resto")
    void probarConexionSinConexion() {
        arca.sinConexion(true);

        PruebaConexionArca prueba = configuracionArca.probarConexion();

        assertThat(prueba.exitosa()).isFalse();
        assertThat(prueba.primerError().nombre()).isEqualTo("Conexión con ARCA");
        assertThat(prueba.primerError().detalle()).contains("conexión a internet");
        assertThat(prueba.pasos()).extracting(PruebaConexionArca.Paso::resultado).containsExactly(
                PruebaConexionArca.Resultado.CORRECTO, PruebaConexionArca.Resultado.ERROR,
                PruebaConexionArca.Resultado.SIN_VERIFICAR, PruebaConexionArca.Resultado.SIN_VERIFICAR);
    }

    @Test
    @DisplayName("Un punto de venta que ARCA no acepta se informa como error del punto de venta")
    void probarConexionPuntoDeVentaInvalido() {
        arca.puntoVentaInvalido(true);

        try {
            PruebaConexionArca prueba = configuracionArca.probarConexion();

            assertThat(prueba.primerError().nombre()).isEqualTo("Punto de venta " + PUNTO_VENTA);
            assertThat(prueba.primerError().detalle()).contains("Web Services");
        } finally {
            arca.puntoVentaInvalido(false);
        }
    }

    @Test
    @DisplayName("Sin FACTURACION_CONFIGURAR no se ve ni se cambia la configuración")
    void configuracionRequierePermiso() {
        sesionUsuario.iniciarSesion(usuario, EnumSet.complementOf(EnumSet.of(PermisoCodigo.FACTURACION_CONFIGURAR)));

        assertThatThrownBy(() -> configuracionArca.obtener()).isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> configuracionArca.probarConexion()).isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("Reintentar todos los pendientes requiere FACTURACION_REINTENTAR")
    void reintentarRequierePermiso() {
        sesionUsuario.iniciarSesion(usuario, EnumSet.of(PermisoCodigo.VENTA_CREAR, PermisoCodigo.VENTA_VER));

        assertThatThrownBy(() -> facturacion.autorizarPendientes()).isInstanceOf(AuthorizationException.class);
    }

}
