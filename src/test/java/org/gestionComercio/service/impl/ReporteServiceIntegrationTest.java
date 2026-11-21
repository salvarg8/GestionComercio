package org.gestionComercio.service.impl;

import jakarta.persistence.EntityManager;
import org.gestionComercio.config.AuditorProvider;
import org.gestionComercio.config.JpaAuditingConfig;
import org.gestionComercio.dto.reporte.Reporte;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Compra;
import org.gestionComercio.entity.DetalleVenta;
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
import org.gestionComercio.enums.EstadoCompra;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.enums.TipoReporte;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ReporteService;
import org.gestionComercio.support.PostgresTestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Genera los reportes contra un PostgreSQL real (Testcontainers) y verifica
 * que los totales coincidan con los datos cargados, que las ventas y compras
 * anuladas o canceladas no sumen y que se exijan los permisos.
 *
 * <p>Cada test carga sus datos en días propios del año 2000, así que los
 * reportes por período no ven datos de otros tests. Todo se revierte al
 * terminar cada test.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        PostgresTestcontainersConfig.class,
        ReporteServiceImpl.class,
        SesionUsuario.class,
        JpaAuditingConfig.class,
        AuditorProvider.class
})
class ReporteServiceIntegrationTest {

    private static final AtomicLong SECUENCIA = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    /** Cada test usa un bloque de días distinto. */
    private static final AtomicLong BLOQUE = new AtomicLong();

    @Autowired
    private ReporteService reporteService;

    @Autowired
    private SesionUsuario sesionUsuario;

    @Autowired
    private EntityManager entityManager;

    private String sufijo;
    private LocalDate dia1;
    private LocalDate dia2;

    private Usuario ana;
    private Usuario beto;
    private Caja cajaAna;
    private Categoria categoria;
    private Marca marca;
    private Proveedor proveedor;

    @BeforeEach
    void setUp() {
        sesionUsuario.cerrarSesion();

        sufijo = String.valueOf(SECUENCIA.incrementAndGet());
        dia1 = LocalDate.of(2000, 1, 1).plusDays(BLOQUE.getAndIncrement() * 10);
        dia2 = dia1.plusDays(1);

        Empresa empresa = Empresa.builder()
                .razonSocial("Empresa " + sufijo).cuit("30" + sufijo)
                .condicionIva(CondicionIVA.RESPONSABLE_INSCRIPTO)
                .build();
        Rol rol = Rol.builder().nombre("ROL" + sufijo).build();
        entityManager.persist(empresa);
        entityManager.persist(rol);

        ana = usuario(empresa, rol, "Ana", "Pérez", "a");
        beto = usuario(empresa, rol, "Beto", "Gómez", "b");

        categoria = Categoria.builder().nombre("Categoria " + sufijo).build();
        marca = Marca.builder().nombre("Marca " + sufijo).build();
        proveedor = Proveedor.builder().razonSocial("Proveedor " + sufijo).build();
        entityManager.persist(categoria);
        entityManager.persist(marca);
        entityManager.persist(proveedor);

        cajaAna = Caja.builder()
                .fechaApertura(dia1.atTime(8, 0))
                .saldoInicial(new BigDecimal("1000.00"))
                .estadoCaja(EstadoCaja.ABIERTA)
                .usuario(ana)
                .usuarioApertura(ana)
                .build();
        entityManager.persist(cajaAna);

        sesionUsuario.iniciarSesion(ana, EnumSet.allOf(PermisoCodigo.class));
    }

    // ------------------------------------------------------------------
    // Ventas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Ventas por período: un renglón por día, sin anuladas ni canceladas ni ventas fuera del período")
    void ventasPorPeriodo() {
        cargarVentas();

        Reporte reporte = reporteService.generar(TipoReporte.VENTAS_POR_PERIODO, dia1, dia2);

        assertThat(reporte.filas()).hasSize(2);

        List<Object> primero = reporte.filas().get(0);
        assertThat(primero.get(0)).isEqualTo(dia1);
        assertThat(primero.get(1)).isEqualTo(2L);
        assertImporte(primero.get(2), "1600");
        assertImporte(primero.get(3), "100");
        assertImporte(primero.get(4), "1500");

        List<Object> segundo = reporte.filas().get(1);
        assertThat(segundo.get(0)).isEqualTo(dia2);
        assertThat(segundo.get(1)).isEqualTo(1L);
        assertImporte(segundo.get(4), "300");

        assertThat(reporte.totales().get(1)).isEqualTo(3L);
        assertImporte(reporte.totales().get(2), "1900");
        assertImporte(reporte.totales().get(3), "100");
        assertImporte(reporte.totales().get(4), "1800");
        assertThat(reporte.filtros()).isEqualTo("Del " + fecha(dia1) + " al " + fecha(dia2));
        assertThat(reporte.empresa()).isNotNull();
    }

    @Test
    @DisplayName("Ventas por forma de pago: totales y porcentajes sobre lo vendido, sin anuladas")
    void ventasPorFormaPago() {
        cargarVentas();

        Reporte reporte = reporteService.generar(TipoReporte.VENTAS_POR_FORMA_PAGO, dia1, dia2);

        assertThat(reporte.filas()).hasSize(2);
        assertThat(reporte.filas().get(0)).element(0).isEqualTo(FormaPago.EFECTIVO.getDescripcion());
        assertThat(reporte.filas().get(0).get(1)).isEqualTo(2L);
        assertImporte(reporte.filas().get(0).get(2), "1500");
        assertImporte(reporte.filas().get(0).get(3), "0.8333");
        assertThat(reporte.filas().get(1)).element(0).isEqualTo(FormaPago.TARJETA_DEBITO.getDescripcion());
        assertImporte(reporte.filas().get(1).get(3), "0.1667");

        assertThat(reporte.totales().get(1)).isEqualTo(3L);
        assertImporte(reporte.totales().get(2), "1800");
        assertImporte(reporte.totales().get(3), "1");
    }

    @Test
    @DisplayName("Ventas por usuario: lo vendido por cada vendedor y su promedio por venta")
    void ventasPorUsuario() {
        cargarVentas();

        Reporte reporte = reporteService.generar(TipoReporte.VENTAS_POR_USUARIO, dia1, dia2);

        assertThat(reporte.filas()).hasSize(2);
        assertThat(reporte.filas().get(0)).element(0).isEqualTo("Ana Pérez");
        assertThat(reporte.filas().get(0).get(1)).isEqualTo(2L);
        assertImporte(reporte.filas().get(0).get(2), "1300");
        assertImporte(reporte.filas().get(0).get(3), "650");
        assertThat(reporte.filas().get(1)).element(0).isEqualTo("Beto Gómez");
        assertImporte(reporte.filas().get(1).get(2), "500");

        assertThat(reporte.totales().get(1)).isEqualTo(3L);
        assertImporte(reporte.totales().get(2), "1800");
        assertImporte(reporte.totales().get(3), "600");
    }

    @Test
    @DisplayName("Productos más vendidos: ordenados por cantidad, sin los renglones de ventas anuladas")
    void productosMasVendidos() {
        cargarVentas();

        Reporte reporte = reporteService.generar(TipoReporte.PRODUCTOS_MAS_VENDIDOS, dia1, dia2);

        assertThat(reporte.filas()).hasSize(2);
        assertThat(reporte.filas().get(0).get(0)).isEqualTo(1L);
        assertThat(reporte.filas().get(0).get(2)).isEqualTo("Yerba " + sufijo);
        assertImporte(reporte.filas().get(0).get(4), "5");
        assertImporte(reporte.filas().get(0).get(5), "1000");
        assertThat(reporte.filas().get(1).get(2)).isEqualTo("Queso " + sufijo);
        assertImporte(reporte.filas().get(1).get(4), "1.5");
        assertImporte(reporte.filas().get(1).get(5), "900");

        assertImporte(reporte.totales().get(5), "1900");
    }

    @Test
    @DisplayName("Un período sin ventas devuelve el reporte vacío con totales en cero")
    void periodoSinVentas() {
        Reporte reporte = reporteService.generar(TipoReporte.VENTAS_POR_PERIODO, dia1, dia2);

        assertThat(reporte.filas()).isEmpty();
        assertThat(reporte.totales().get(1)).isEqualTo(0L);
        assertImporte(reporte.totales().get(4), "0");
    }

    // ------------------------------------------------------------------
    // Inventario
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Valorización: stock × precio de compra de los productos con stock; el total es la suma de las filas")
    void valorizacionInventario() {
        producto("Arroz", "10", "250.50", "2", true);
        producto("Harina", "2.5", "100", "1", true);
        producto("SinStock", "0", "999", "1", true);
        producto("Servicio", "5", "999", "0", false);
        flush();

        Reporte reporte = reporteService.generar(TipoReporte.VALORIZACION_INVENTARIO, null, null);

        assertThat(filaDe(reporte, 1, "Arroz " + sufijo).get(5)).satisfies(v -> assertImporte(v, "2505"));
        assertThat(filaDe(reporte, 1, "Harina " + sufijo).get(5)).satisfies(v -> assertImporte(v, "250"));
        assertThat(reporte.filas()).noneMatch(f -> f.get(1).equals("SinStock " + sufijo)
                || f.get(1).equals("Servicio " + sufijo));

        BigDecimal suma = reporte.filas().stream()
                .map(f -> (BigDecimal) f.get(5))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertImporte(reporte.totales().get(5), suma.toPlainString());
        assertThat(reporte.filtros()).startsWith("Situación al ");
    }

    @Test
    @DisplayName("Stock bajo: incluye los productos en el mínimo o debajo y los sin stock")
    void stockBajo() {
        producto("Normal", "10", "1", "2", true);
        producto("Bajo", "2", "1", "2", true);
        producto("Agotado", "0", "1", "2", true);
        flush();

        Reporte reporte = reporteService.generar(TipoReporte.STOCK_BAJO, null, null);

        assertThat(filaDe(reporte, 1, "Bajo " + sufijo).get(5)).isEqualTo("Stock bajo");
        assertThat(filaDe(reporte, 1, "Agotado " + sufijo).get(5)).isEqualTo("Sin stock");
        assertThat(reporte.filas()).noneMatch(f -> f.get(1).equals("Normal " + sufijo));
    }

    // ------------------------------------------------------------------
    // Compras y caja
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Compras por proveedor: sin compras anuladas ni canceladas")
    void comprasPorProveedor() {
        Proveedor otro = Proveedor.builder().razonSocial("Otro " + sufijo).build();
        entityManager.persist(otro);

        compra(proveedor, dia1.atTime(9, 0), "1000", EstadoCompra.RECIBIDA);
        compra(proveedor, dia2.atTime(9, 0), "500", EstadoCompra.RECIBIDA);
        compra(proveedor, dia2.atTime(10, 0), "7000", EstadoCompra.ANULADA);
        compra(otro, dia2.atTime(11, 0), "200", EstadoCompra.RECIBIDA);
        compra(otro, dia2.atTime(12, 0), "300", EstadoCompra.CANCELADA);
        compra(otro, dia2.plusDays(1).atTime(9, 0), "9000", EstadoCompra.RECIBIDA);
        flush();

        Reporte reporte = reporteService.generar(TipoReporte.COMPRAS_POR_PROVEEDOR, dia1, dia2);

        assertThat(reporte.filas()).hasSize(2);
        assertThat(reporte.filas().get(0).get(0)).isEqualTo("Proveedor " + sufijo);
        assertThat(reporte.filas().get(0).get(2)).isEqualTo(2L);
        assertImporte(reporte.filas().get(0).get(3), "1500");
        assertThat(reporte.filas().get(1).get(0)).isEqualTo("Otro " + sufijo);
        assertImporte(reporte.filas().get(1).get(3), "200");

        assertThat(reporte.totales().get(2)).isEqualTo(3L);
        assertImporte(reporte.totales().get(3), "1700");
    }

    @Test
    @DisplayName("Movimientos de caja: ingresos y egresos del período, sin la apertura, con su neto")
    void movimientosCaja() {
        movimiento(dia1.atTime(8, 0), TipoMovimientoCaja.APERTURA, "1000");
        movimiento(dia1.atTime(9, 0), TipoMovimientoCaja.VENTA, "1500");
        movimiento(dia1.atTime(10, 0), TipoMovimientoCaja.GASTO, "200");
        movimiento(dia2.atTime(9, 0), TipoMovimientoCaja.ANULACION_VENTA, "300");
        movimiento(dia2.atTime(10, 0), TipoMovimientoCaja.INGRESO_MANUAL, "50");
        movimiento(dia2.plusDays(1).atTime(9, 0), TipoMovimientoCaja.VENTA, "9999");
        flush();

        Reporte reporte = reporteService.generar(TipoReporte.MOVIMIENTOS_CAJA, dia1, dia2);

        assertThat(reporte.filas()).hasSize(4);
        assertThat(reporte.filas()).extracting(f -> f.get(2)).containsExactly(
                "Venta", "Gasto", "Anulación de venta", "Ingreso manual");
        assertThat(reporte.filas().get(0).get(1)).isEqualTo("Ana Pérez");
        assertImporte(reporte.filas().get(0).get(5), "1500");
        assertThat(reporte.filas().get(0).get(6)).isNull();
        assertImporte(reporte.filas().get(1).get(6), "200");

        assertImporte(reporte.totales().get(5), "1550");
        assertImporte(reporte.totales().get(6), "500");
        assertThat((String) reporte.totales().get(3)).startsWith("Neto: ").contains("1.050,00");
    }

    // ------------------------------------------------------------------
    // Permisos y validaciones
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Sólo se ofrecen y se generan los reportes cuyo permiso tiene el usuario")
    void permisos() {
        sesionUsuario.iniciarSesion(ana, EnumSet.of(PermisoCodigo.REPORTE_INVENTARIO));

        assertThat(reporteService.disponibles())
                .containsExactly(TipoReporte.VALORIZACION_INVENTARIO, TipoReporte.STOCK_BAJO);

        assertThatThrownBy(() -> reporteService.generar(TipoReporte.VENTAS_POR_PERIODO, dia1, dia2))
                .isInstanceOf(AuthorizationException.class);

        sesionUsuario.cerrarSesion();

        assertThat(reporteService.disponibles()).isEmpty();
        assertThatThrownBy(() -> reporteService.generar(TipoReporte.STOCK_BAJO, null, null))
                .isInstanceOf(AuthorizationException.class);
    }

    @Test
    @DisplayName("Rechaza un período con la fecha desde posterior a la fecha hasta")
    void periodoInvalido() {
        assertThatThrownBy(() -> reporteService.generar(TipoReporte.VENTAS_POR_PERIODO, dia2, dia1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("posterior");

        assertThatThrownBy(() -> reporteService.generar(null, dia1, dia2))
                .isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------------
    // Datos
    // ------------------------------------------------------------------

    /**
     * Día 1, en efectivo: Ana vende 600 + 500 − 100 de descuento = 1000 y
     * Beto 200 + 300 = 500; además, una venta anulada y una cancelada.
     * Día 2 (a las 23:59:59): Ana vende 200 + 100 = 300 con débito. Día 3,
     * fuera del período: una venta grande.
     *
     * <p>Yerba vendida: 3 + 1 + 1 = 5 por 1000; queso: 0,5 × 3 = 1,5 por 900.
     */
    private void cargarVentas() {
        Producto yerba = producto("Yerba", "100", "150", "0", true);
        Producto queso = producto("Queso", "100", "400", "0", true);

        venta(ana, dia1.atTime(10, 0), FormaPago.EFECTIVO, "100", EstadoVenta.PAGADA,
                renglon(yerba, "3", "200"), renglon(queso, "0.5", "1000"));
        venta(beto, dia1.atTime(11, 0), FormaPago.EFECTIVO, "0", EstadoVenta.PAGADA,
                renglon(yerba, "1", "200"), renglon(queso, "0.5", "600"));
        venta(ana, dia1.atTime(12, 0), FormaPago.EFECTIVO, "0", EstadoVenta.ANULADA,
                renglon(yerba, "40", "200"));
        venta(beto, dia1.atTime(13, 0), FormaPago.TARJETA_CREDITO, "0", EstadoVenta.CANCELADA,
                renglon(queso, "10", "700"));
        venta(ana, dia2.atTime(23, 59, 59), FormaPago.TARJETA_DEBITO, "0", EstadoVenta.PAGADA,
                renglon(yerba, "1", "200"), renglon(queso, "0.5", "200"));
        venta(ana, dia2.plusDays(1).atStartOfDay(), FormaPago.EFECTIVO, "0", EstadoVenta.PAGADA,
                renglon(yerba, "99", "200"));
        flush();
    }

    private Usuario usuario(Empresa empresa, Rol rol, String nombre, String apellido, String letra) {
        Usuario usuario = Usuario.builder()
                .usuario("user" + letra + sufijo).password("x").nombre(nombre).apellido(apellido)
                .empresa(empresa).rol(rol).tipoDocumento(TipoDocumento.DNI).documento(letra + sufijo)
                .build();
        entityManager.persist(usuario);
        return usuario;
    }

    private Producto producto(String nombre, String stock, String precioCompra, String minimo, boolean manejaStock) {
        Producto producto = Producto.builder()
                .codigo(nombre + sufijo).nombre(nombre + " " + sufijo)
                .unidadMedida(UnidadMedida.UNIDAD)
                .precioCompra(new BigDecimal(precioCompra))
                .precioVenta(new BigDecimal("1"))
                .stock(new BigDecimal(stock))
                .stockMinimo(new BigDecimal(minimo))
                .manejaStock(manejaStock)
                .categoria(categoria).marca(marca).proveedor(proveedor)
                .build();
        entityManager.persist(producto);
        return producto;
    }

    private record Renglon(Producto producto, BigDecimal cantidad, BigDecimal precio) {
    }

    private static Renglon renglon(Producto producto, String cantidad, String precio) {
        return new Renglon(producto, new BigDecimal(cantidad), new BigDecimal(precio));
    }

    private void venta(Usuario vendedor, LocalDateTime fecha, FormaPago formaPago, String descuento,
                       EstadoVenta estado, Renglon... renglones) {

        Venta venta = Venta.builder()
                .fecha(fecha)
                .tipoComprobante(TipoComprobante.TICKET)
                .numeroComprobante(SECUENCIA.incrementAndGet() * 100)
                .formaPago(formaPago)
                .usuario(vendedor)
                .caja(cajaAna)
                .estadoVenta(estado)
                .descuento(new BigDecimal(descuento))
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (Renglon renglon : renglones) {
            BigDecimal importe = DetalleVenta.calcularSubtotal(renglon.cantidad(), renglon.precio());
            venta.getDetalles().add(DetalleVenta.builder()
                    .venta(venta).producto(renglon.producto())
                    .cantidad(renglon.cantidad()).precioUnitario(renglon.precio()).subtotal(importe)
                    .build());
            subtotal = subtotal.add(importe);
        }

        venta.setSubtotal(subtotal);
        venta.setTotal(subtotal.subtract(venta.getDescuento()));

        if (estado == EstadoVenta.ANULADA) {
            venta.setFechaAnulacion(fecha.plusMinutes(5));
            venta.setMotivoAnulacion("Prueba");
            venta.setUsuarioAnulacion(vendedor);
        }

        entityManager.persist(venta);
    }

    private void compra(Proveedor deQuien, LocalDateTime fecha, String total, EstadoCompra estado) {

        Compra compra = Compra.builder()
                .fecha(fecha)
                .subtotal(new BigDecimal(total))
                .total(new BigDecimal(total))
                .formaPago(FormaPago.TRANSFERENCIA)
                .proveedor(deQuien)
                .usuario(ana)
                .estadoCompra(estado)
                .build();

        if (estado == EstadoCompra.ANULADA) {
            compra.setFechaAnulacion(fecha.plusMinutes(5));
            compra.setMotivoAnulacion("Prueba");
            compra.setUsuarioAnulacion(ana);
        }

        entityManager.persist(compra);
    }

    private void movimiento(LocalDateTime fecha, TipoMovimientoCaja tipo, String importe) {
        entityManager.persist(MovimientoCaja.builder()
                .fecha(fecha).tipo(tipo).descripcion(tipo.getDescripcion())
                .importe(new BigDecimal(importe))
                .caja(cajaAna).usuario(ana)
                .build());
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private static List<Object> filaDe(Reporte reporte, int columna, String valor) {
        return reporte.filas().stream()
                .filter(f -> Objects.equals(f.get(columna), valor))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No está la fila de " + valor));
    }

    private static void assertImporte(Object valor, String esperado) {
        assertThat((BigDecimal) valor).isEqualByComparingTo(esperado);
    }

    private static String fecha(LocalDate dia) {
        return String.format("%02d/%02d/%d", dia.getDayOfMonth(), dia.getMonthValue(), dia.getYear());
    }
}
