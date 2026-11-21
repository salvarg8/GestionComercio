package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.dto.reporte.ColumnaReporte;
import org.gestionComercio.dto.reporte.ComprasPorProveedor;
import org.gestionComercio.dto.reporte.FormatoDato;
import org.gestionComercio.dto.reporte.ProductoValorizado;
import org.gestionComercio.dto.reporte.ProductoVendido;
import org.gestionComercio.dto.reporte.Reporte;
import org.gestionComercio.dto.reporte.VentasPorDia;
import org.gestionComercio.dto.reporte.VentasPorFormaPago;
import org.gestionComercio.dto.reporte.VentasPorUsuario;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.FiltroStock;
import org.gestionComercio.enums.SituacionStock;
import org.gestionComercio.enums.TipoReporte;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.repository.CompraRepository;
import org.gestionComercio.repository.DetalleVentaRepository;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.VentaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ReporteService;
import org.gestionComercio.util.CuitUtils;
import org.gestionComercio.util.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.gestionComercio.dto.reporte.ColumnaReporte.de;
import static org.gestionComercio.dto.reporte.ColumnaReporte.texto;
import static org.gestionComercio.dto.reporte.Reporte.fila;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReporteServiceImpl implements ReporteService {

    static final String TOTAL = "Total";

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final CompraRepository compraRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final ProductoRepository productoRepository;
    private final EmpresaRepository empresaRepository;
    private final SesionUsuario sesionUsuario;

    @Override
    public List<TipoReporte> disponibles() {

        if (!sesionUsuario.estaAutenticado()) {
            return List.of();
        }

        return TipoReporte.permitidos(sesionUsuario::tienePermiso);
    }

    @Override
    public Reporte generar(TipoReporte tipo, LocalDate desde, LocalDate hasta) {

        if (tipo == null) {
            throw new BusinessException("Seleccione el reporte a generar.");
        }

        sesionUsuario.requerirPermiso(tipo.getPermiso());

        if (tipo.isUsaFechas() && desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }

        LocalDateTime inicio = tipo.isUsaFechas() && desde != null ? desde.atStartOfDay() : null;
        LocalDateTime fin = tipo.isUsaFechas() && hasta != null ? DateUtils.finDelDia(hasta) : null;
        LocalDateTime ahora = LocalDateTime.now();

        Contenido contenido = switch (tipo) {
            case VENTAS_POR_PERIODO -> ventasPorPeriodo(inicio, fin);
            case VENTAS_POR_FORMA_PAGO -> ventasPorFormaPago(inicio, fin);
            case VENTAS_POR_USUARIO -> ventasPorUsuario(inicio, fin);
            case PRODUCTOS_MAS_VENDIDOS -> productosMasVendidos(inicio, fin);
            case VALORIZACION_INVENTARIO -> valorizacionInventario();
            case STOCK_BAJO -> stockBajo();
            case COMPRAS_POR_PROVEEDOR -> comprasPorProveedor(inicio, fin);
            case MOVIMIENTOS_CAJA -> movimientosCaja(inicio, fin);
        };

        String filtros = tipo.isUsaFechas() ? periodo(desde, hasta) : "Situación al " + DateUtils.format(ahora);

        return new Reporte(tipo, empresa(), filtros, ahora,
                contenido.columnas(), contenido.filas(), contenido.totales());
    }

    // ------------------------------------------------------------------
    // Ventas
    // ------------------------------------------------------------------

    private Contenido ventasPorPeriodo(LocalDateTime desde, LocalDateTime hasta) {

        List<VentasPorDia> dias = ventaRepository.totalizarPorDia(desde, hasta);

        List<List<Object>> filas = new ArrayList<>();
        long cantidad = 0;
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal descuento = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (VentasPorDia dia : dias) {
            filas.add(fila(dia.fecha(), dia.cantidad(), dia.subtotal(), dia.descuento(), dia.total()));
            cantidad += dia.cantidad();
            subtotal = subtotal.add(dia.subtotal());
            descuento = descuento.add(dia.descuento());
            total = total.add(dia.total());
        }

        return new Contenido(
                List.of(de("Fecha", FormatoDato.FECHA),
                        de("Ventas", FormatoDato.ENTERO),
                        de("Subtotal", FormatoDato.IMPORTE),
                        de("Descuentos", FormatoDato.IMPORTE),
                        de("Total", FormatoDato.IMPORTE)),
                filas,
                fila(TOTAL, cantidad, subtotal, descuento, total));
    }

    private Contenido ventasPorFormaPago(LocalDateTime desde, LocalDateTime hasta) {

        List<VentasPorFormaPago> formas = ventaRepository.totalizarPorFormaPago(desde, hasta);

        long cantidad = formas.stream().mapToLong(VentasPorFormaPago::cantidad).sum();
        BigDecimal total = formas.stream().map(VentasPorFormaPago::total).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<List<Object>> filas = new ArrayList<>();

        for (VentasPorFormaPago forma : formas) {
            filas.add(fila(forma.formaPago().getDescripcion(), forma.cantidad(), forma.total(),
                    porcentaje(forma.total(), total)));
        }

        return new Contenido(
                List.of(texto("Forma de pago"),
                        de("Ventas", FormatoDato.ENTERO),
                        de("Total", FormatoDato.IMPORTE),
                        de("% del total", FormatoDato.PORCENTAJE)),
                filas,
                fila(TOTAL, cantidad, total, total.signum() == 0 ? null : BigDecimal.ONE));
    }

    private Contenido ventasPorUsuario(LocalDateTime desde, LocalDateTime hasta) {

        List<VentasPorUsuario> usuarios = ventaRepository.totalizarPorUsuario(desde, hasta);

        List<List<Object>> filas = new ArrayList<>();
        long cantidad = 0;
        BigDecimal total = BigDecimal.ZERO;

        for (VentasPorUsuario usuario : usuarios) {
            filas.add(fila(usuario.usuarioNombreCompleto(), usuario.cantidad(), usuario.total(),
                    promedio(usuario.total(), usuario.cantidad())));
            cantidad += usuario.cantidad();
            total = total.add(usuario.total());
        }

        return new Contenido(
                List.of(texto("Vendedor"),
                        de("Ventas", FormatoDato.ENTERO),
                        de("Total", FormatoDato.IMPORTE),
                        de("Promedio por venta", FormatoDato.IMPORTE)),
                filas,
                fila(TOTAL, cantidad, total, promedio(total, cantidad)));
    }

    private Contenido productosMasVendidos(LocalDateTime desde, LocalDateTime hasta) {

        List<ProductoVendido> productos = detalleVentaRepository.totalizarPorProducto(desde, hasta);

        List<List<Object>> filas = new ArrayList<>();
        BigDecimal importe = BigDecimal.ZERO;
        int posicion = 0;

        for (ProductoVendido producto : productos) {
            filas.add(fila((long) ++posicion, producto.codigo(), producto.nombre(),
                    producto.unidadMedida() == null ? null : producto.unidadMedida().getDescripcion(),
                    producto.cantidad(), producto.importe()));
            importe = importe.add(producto.importe());
        }

        // Las cantidades no se suman: pueden estar en unidades distintas.
        return new Contenido(
                List.of(de("Puesto", FormatoDato.ENTERO),
                        texto("Código"),
                        texto("Producto"),
                        texto("Unidad"),
                        de("Cantidad vendida", FormatoDato.CANTIDAD),
                        de("Importe", FormatoDato.IMPORTE)),
                filas,
                fila(null, TOTAL, cantidadDe(productos.size(), "producto", "productos"), null, null, importe));
    }

    // ------------------------------------------------------------------
    // Inventario
    // ------------------------------------------------------------------

    private Contenido valorizacionInventario() {

        List<ProductoValorizado> productos = productoRepository.consultarValorizacion();

        List<List<Object>> filas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (ProductoValorizado producto : productos) {
            BigDecimal valor = producto.valor();
            filas.add(fila(producto.codigo(), producto.nombre(), producto.categoriaNombre(),
                    producto.stock(), producto.precioCompra(), valor));
            total = total.add(valor);
        }

        return new Contenido(
                List.of(texto("Código"),
                        texto("Producto"),
                        texto("Categoría"),
                        de("Stock", FormatoDato.CANTIDAD),
                        de("Precio de compra", FormatoDato.IMPORTE),
                        de("Valor", FormatoDato.IMPORTE)),
                filas,
                fila(TOTAL, cantidadDe(productos.size(), "producto", "productos"), null, null, null, total));
    }

    private Contenido stockBajo() {

        List<StockProductoDto> productos =
                productoRepository.consultarStock("", null, FiltroStock.STOCK_BAJO.name());

        List<List<Object>> filas = new ArrayList<>();
        long sinStock = 0;

        for (StockProductoDto producto : productos) {
            SituacionStock situacion = producto.situacion();
            filas.add(fila(producto.codigo(), producto.nombre(), producto.categoriaNombre(),
                    producto.stock(), producto.stockMinimo(), situacion.getDescripcion()));
            if (situacion == SituacionStock.SIN_STOCK) {
                sinStock++;
            }
        }

        return new Contenido(
                List.of(texto("Código"),
                        texto("Producto"),
                        texto("Categoría"),
                        de("Stock", FormatoDato.CANTIDAD),
                        de("Stock mínimo", FormatoDato.CANTIDAD),
                        texto("Situación")),
                filas,
                fila(TOTAL, cantidadDe(productos.size(), "producto", "productos"), null, null, null,
                        sinStock + " sin stock"));
    }

    // ------------------------------------------------------------------
    // Compras y caja
    // ------------------------------------------------------------------

    private Contenido comprasPorProveedor(LocalDateTime desde, LocalDateTime hasta) {

        List<ComprasPorProveedor> proveedores = compraRepository.totalizarPorProveedor(desde, hasta);

        List<List<Object>> filas = new ArrayList<>();
        long cantidad = 0;
        BigDecimal total = BigDecimal.ZERO;

        for (ComprasPorProveedor proveedor : proveedores) {
            filas.add(fila(proveedor.razonSocial(),
                    proveedor.cuit() == null ? null : CuitUtils.formatear(proveedor.cuit()),
                    proveedor.cantidad(), proveedor.total()));
            cantidad += proveedor.cantidad();
            total = total.add(proveedor.total());
        }

        return new Contenido(
                List.of(texto("Proveedor"),
                        texto("CUIT"),
                        de("Compras", FormatoDato.ENTERO),
                        de("Total", FormatoDato.IMPORTE)),
                filas,
                fila(TOTAL, null, cantidad, total));
    }

    private Contenido movimientosCaja(LocalDateTime desde, LocalDateTime hasta) {

        List<MovimientoCaja> movimientos = movimientoCajaRepository.buscarIngresosYEgresos(desde, hasta);

        List<List<Object>> filas = new ArrayList<>();
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal egresos = BigDecimal.ZERO;

        for (MovimientoCaja movimiento : movimientos) {

            boolean ingreso = movimiento.getTipo().esIngreso();
            BigDecimal importe = movimiento.getImporte();

            filas.add(fila(movimiento.getFecha(),
                    nombreCompleto(movimiento.getCaja().getUsuario()),
                    movimiento.getTipo().getDescripcion(),
                    movimiento.getDescripcion(),
                    nombreCompleto(movimiento.getUsuario()),
                    ingreso ? importe : null,
                    ingreso ? null : importe));

            if (ingreso) {
                ingresos = ingresos.add(importe);
            } else {
                egresos = egresos.add(importe);
            }
        }

        BigDecimal neto = ingresos.subtract(egresos);

        return new Contenido(
                List.of(de("Fecha", FormatoDato.FECHA_HORA),
                        texto("Caja de"),
                        texto("Tipo"),
                        texto("Descripción"),
                        texto("Registró"),
                        de("Ingreso", FormatoDato.IMPORTE),
                        de("Egreso", FormatoDato.IMPORTE)),
                filas,
                fila(TOTAL, null, null, "Neto: " + FormatoDato.IMPORTE.formatear(neto), null, ingresos, egresos));
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Columnas, filas y totales de un reporte. */
    private record Contenido(List<ColumnaReporte> columnas, List<List<Object>> filas, List<Object> totales) {
    }

    private String empresa() {
        return empresaRepository.findFirstByOrderByIdAsc()
                .map(Empresa::getRazonSocial)
                .orElse("");
    }

    static String periodo(LocalDate desde, LocalDate hasta) {

        if (desde != null && hasta != null) {
            return desde.equals(hasta)
                    ? "Día " + DateUtils.format(desde)
                    : "Del " + DateUtils.format(desde) + " al " + DateUtils.format(hasta);
        }

        if (desde != null) {
            return "Desde el " + DateUtils.format(desde);
        }

        if (hasta != null) {
            return "Hasta el " + DateUtils.format(hasta);
        }

        return "Todas las fechas";
    }

    /** Parte sobre total como fracción con cuatro decimales; null si el total es cero. */
    static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? null : parte.divide(total, 4, RoundingMode.HALF_UP);
    }

    /** Importe promedio por operación, en centavos; null si no hay operaciones. */
    static BigDecimal promedio(BigDecimal total, long cantidad) {
        return cantidad == 0 ? null : total.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
    }

    private static String cantidadDe(long cantidad, String singular, String plural) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }

    private static String nombreCompleto(Usuario usuario) {
        return usuario == null ? "" : usuario.getNombre() + " " + usuario.getApellido();
    }
}
