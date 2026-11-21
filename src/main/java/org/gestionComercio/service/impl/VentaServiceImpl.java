package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.dto.inventario.LineaInventario;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.ResumenVentas;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.dto.venta.VentasPorCaja;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.entity.DetalleVenta;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.entity.Venta;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.ClienteMapper;
import org.gestionComercio.mapper.ProductoMapper;
import org.gestionComercio.mapper.VentaMapper;
import org.gestionComercio.repository.CajaRepository;
import org.gestionComercio.repository.ClienteRepository;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.NumeradorComprobanteRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.VentaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.InventarioComprobanteService;
import org.gestionComercio.service.VentaService;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.validation.ValidationResult;
import org.gestionComercio.validation.VentaValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cualquier excepción revierte la transacción completa: venta, stock y caja
 * se confirman o se descartan juntos.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class VentaServiceImpl implements VentaService {

    /** Largo de las columnas de texto (motivos y descripciones). */
    static final int TEXTO_MAX = 255;

    private final VentaRepository ventaRepository;
    private final NumeradorComprobanteRepository numeradorComprobanteRepository;
    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final InventarioComprobanteService inventarioComprobanteService;
    private final VentaValidator ventaValidator;
    private final VentaMapper ventaMapper;
    private final ProductoMapper productoMapper;
    private final ClienteMapper clienteMapper;
    private final SesionUsuario sesionUsuario;

    @Override
    public VentaDto registrar(VentaDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_CREAR);

        if (dto == null) {
            throw new BusinessException("Debe indicar los datos de la venta.");
        }

        Usuario usuario = sesionUsuario.getUsuario();
        Caja caja = cajaRepository
                .findFirstByUsuarioIdAndEstadoCajaOrderByFechaAperturaDesc(usuario.getId(), EstadoCaja.ABIERTA)
                .orElseThrow(() -> new BusinessException(
                        "No tiene una caja abierta. Debe abrir la caja antes de registrar una venta."));

        normalizar(dto);

        ValidationResult result = ventaValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }

        Venta venta = armarVenta(dto, usuario, caja);
        venta.setNumeroComprobante(numeradorComprobanteRepository.siguiente(venta.getTipoComprobante()));

        venta = ventaRepository.save(venta);

        String referencia = TipoComprobante.formatear(venta.getTipoComprobante(), venta.getNumeroComprobante());

        inventarioComprobanteService.registrarSalidas(
                OrigenMovimientoInventario.VENTA, venta.getId(), lineasDeInventario(venta), referencia);

        if (venta.getFormaPago() == FormaPago.EFECTIVO && venta.getTotal().signum() > 0) {

            movimientoCajaRepository.save(MovimientoCaja.builder()
                    .fecha(venta.getFecha())
                    .tipo(TipoMovimientoCaja.VENTA)
                    .descripcion(referencia)
                    .importe(venta.getTotal())
                    .caja(caja)
                    .usuario(usuario)
                    .build());
        }

        return ventaMapper.toDto(venta);
    }

    @Override
    public VentaDto anular(Long ventaId, String motivo) {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_ANULAR);

        if (ventaId == null) {
            throw new BusinessException("La venta es obligatoria.");
        }

        String motivoAnulacion = validarMotivo(motivo);

        Venta venta = ventaRepository.findParaModificar(ventaId)
                .orElseThrow(() -> new EntityNotFoundException("No existe una venta con ID: " + ventaId));

        String referencia = "venta "
                + TipoComprobante.formatear(venta.getTipoComprobante(), venta.getNumeroComprobante());

        if (venta.getEstadoVenta() == EstadoVenta.ANULADA) {
            throw new BusinessException("La " + referencia + " ya está anulada.");
        }

        if (venta.getEstadoVenta() != EstadoVenta.PAGADA && venta.getEstadoVenta() != EstadoVenta.PENDIENTE) {
            throw new BusinessException("Sólo se pueden anular ventas pagadas o pendientes; la "
                    + referencia + " está " + venta.getEstadoVenta().getDescripcion().toLowerCase()
                    + ".");
        }

        Usuario usuario = sesionUsuario.getUsuario();

        // Primero la caja: si no se puede devolver el efectivo, no se toca nada.
        boolean devuelveEfectivo = venta.getFormaPago() == FormaPago.EFECTIVO && venta.getTotal().signum() > 0;
        Caja caja = devuelveEfectivo ? cajaParaDevolucion(venta, usuario) : null;

        inventarioComprobanteService.revertir(OrigenMovimientoInventario.VENTA, venta.getId(),
                recortar("Anulación de " + referencia + ": " + motivoAnulacion));

        LocalDateTime ahora = LocalDateTime.now();

        if (devuelveEfectivo) {

            movimientoCajaRepository.save(MovimientoCaja.builder()
                    .fecha(ahora)
                    .tipo(TipoMovimientoCaja.ANULACION_VENTA)
                    .descripcion("Anulación de " + referencia)
                    .importe(venta.getTotal())
                    .caja(caja)
                    .usuario(usuario)
                    .build());
        }

        venta.setEstadoVenta(EstadoVenta.ANULADA);
        venta.setFechaAnulacion(ahora);
        venta.setMotivoAnulacion(motivoAnulacion);
        venta.setUsuarioAnulacion(usuario);

        return ventaMapper.toDto(ventaRepository.save(venta));
    }

    @Transactional(readOnly = true)
    @Override
    public VentaDto obtener(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_VER);

        if (id == null) {
            throw new BusinessException("La venta es obligatoria.");
        }

        return ventaRepository.findConDetalles(id)
                .map(ventaMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("No existe una venta con ID: " + id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<VentaDto> listar(LocalDate desde, LocalDate hasta, EstadoVenta estado) {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_VER);

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }

        return ventaMapper.toResumenList(ventaRepository.buscar(
                desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.atTime(LocalTime.MAX),
                estado));
    }

    @Transactional(readOnly = true)
    @Override
    public List<VentasPorCaja> ventasDelDiaPorCaja() {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_VER);

        // Las cajas abiertas aparecen siempre, aunque hoy no hayan vendido.
        Map<Long, VentasPorCaja> porCaja = new LinkedHashMap<>();

        for (Caja caja : cajaRepository.findAbiertas()) {
            porCaja.put(caja.getId(), new VentasPorCaja(
                    caja.getId(),
                    caja.getUsuario().getNombre() + " " + caja.getUsuario().getApellido(),
                    caja.getEstadoCaja(),
                    caja.getFechaApertura(),
                    0L,
                    BigDecimal.ZERO));
        }

        // Las que vendieron hoy (abiertas o ya cerradas) con sus totales.
        for (VentasPorCaja ventas : totalizarHoyPorCaja(null)) {
            porCaja.put(ventas.cajaId(), ventas);
        }

        return porCaja.values().stream()
                .sorted(Comparator.comparing(VentasPorCaja::fechaApertura)
                        .thenComparing(VentasPorCaja::cajaId))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public ResumenVentas ventasDelDiaPropias() {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_CREAR);

        return ResumenVentas.de(totalizarHoyPorCaja(sesionUsuario.getUsuario().getId()));
    }

    /** @param usuarioId responsable de las cajas; null para todas */
    private List<VentasPorCaja> totalizarHoyPorCaja(Long usuarioId) {

        LocalDate hoy = LocalDate.now();

        return ventaRepository.totalizarPorCaja(hoy.atStartOfDay(), hoy.atTime(LocalTime.MAX), usuarioId);
    }

    // ------------------------------------------------------------------
    // Consultas del punto de venta
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    @Override
    public boolean tieneCajaAbierta() {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_CREAR);

        return cajaRepository.existsByUsuarioIdAndEstadoCaja(sesionUsuario.getUsuario().getId(), EstadoCaja.ABIERTA);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductoDto> buscarProductos(String texto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_CREAR);

        return productoMapper.toDtoList(productoRepository.buscar(
                texto == null ? "" : texto.trim(), EstadoRegistro.ACTIVO));
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<ProductoDto> buscarProductoPorCodigo(String codigo) {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_CREAR);

        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }

        String limpio = codigo.trim();

        return productoRepository.findFirstByCodigoBarrasAndEstadoRegistro(limpio, EstadoRegistro.ACTIVO)
                .or(() -> productoRepository.findFirstByCodigoIgnoreCaseAndEstadoRegistro(limpio, EstadoRegistro.ACTIVO))
                .map(productoMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ClienteDto> listarClientes() {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_CREAR);

        return clienteMapper.toDtoList(clienteRepository.buscar("", "", EstadoRegistro.ACTIVO));
    }

    @Transactional(readOnly = true)
    @Override
    public List<String> verificarStock(List<DetalleVentaDto> detalles) {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_CREAR);

        if (detalles == null || detalles.isEmpty()) {
            return List.of();
        }

        Map<Long, BigDecimal> cantidades = new LinkedHashMap<>();

        for (DetalleVentaDto detalle : detalles) {
            if (detalle != null && detalle.getProductoId() != null && detalle.getCantidad() != null) {
                cantidades.merge(detalle.getProductoId(), detalle.getCantidad(), BigDecimal::add);
            }
        }

        Map<Long, Producto> productos = productoRepository.findAllById(cantidades.keySet()).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        List<String> faltantes = new ArrayList<>();

        cantidades.forEach((productoId, cantidad) -> {

            Producto producto = productos.get(productoId);

            if (producto == null
                    || !Boolean.TRUE.equals(producto.getManejaStock())
                    || Boolean.TRUE.equals(producto.getPermiteStockNegativo())
                    || producto.getStock().compareTo(cantidad) >= 0) {
                return;
            }

            faltantes.add("Stock insuficiente para \"" + producto.getNombre() + "\": disponible "
                    + CantidadUtils.formatear(producto.getStock()) + ", solicitado "
                    + CantidadUtils.formatear(cantidad) + ".");
        });

        return faltantes;
    }

    /** @return el motivo recortado */
    private String validarMotivo(String motivo) {

        if (motivo == null || motivo.isBlank()) {
            throw new BusinessException("Debe indicar el motivo de la anulación.");
        }

        String recortado = motivo.trim();

        if (recortado.length() > TEXTO_MAX) {
            throw new BusinessException("El motivo no puede superar los " + TEXTO_MAX + " caracteres.");
        }

        return recortado;
    }

    private static String recortar(String texto) {
        return texto.length() <= TEXTO_MAX ? texto : texto.substring(0, TEXTO_MAX - 1) + "…";
    }

    /**
     * Caja donde se registra la devolución del efectivo de una venta anulada:
     * la de la venta si sigue abierta, o la caja abierta del usuario que anula.
     * Controla que el efectivo de esa caja alcance para devolver el total.
     */
    private Caja cajaParaDevolucion(Venta venta, Usuario usuario) {

        Caja caja = venta.getCaja().getEstadoCaja() == EstadoCaja.ABIERTA
                ? venta.getCaja()
                : cajaRepository
                .findFirstByUsuarioIdAndEstadoCajaOrderByFechaAperturaDesc(usuario.getId(), EstadoCaja.ABIERTA)
                .orElseThrow(() -> new BusinessException("La venta se cobró en efectivo y su caja ya está "
                        + "cerrada. Abra una caja para registrar la devolución del efectivo."));

        BigDecimal saldo = movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(caja.getId()).stream()
                .map(m -> m.getImporte().multiply(BigDecimal.valueOf(m.getTipo().getSigno())))
                .reduce(caja.getSaldoInicial(), BigDecimal::add);

        if (venta.getTotal().compareTo(saldo) > 0) {
            throw new BusinessException("No hay efectivo suficiente en caja para devolver la venta: total "
                    + CurrencyUtils.format(venta.getTotal()) + ", saldo en caja "
                    + CurrencyUtils.format(saldo) + ".");
        }

        return caja;
    }

    /** Sin tipo = ticket; descuento vacío = sin descuento; lista de renglones nunca nula. */
    private void normalizar(VentaDto dto) {

        if (dto.getTipoComprobante() == null) {
            dto.setTipoComprobante(TipoComprobante.TICKET);
        }

        if (dto.getDescuento() == null) {
            dto.setDescuento(BigDecimal.ZERO);
        }

        if (dto.getDetalles() == null) {
            dto.setDetalles(new ArrayList<>());
        }
    }

    /** Arma la venta ya validada con el precio actual de cada producto y calcula los totales. */
    private Venta armarVenta(VentaDto dto, Usuario usuario, Caja caja) {

        Map<Long, Producto> productos = productoRepository
                .findAllById(dto.getDetalles().stream().map(DetalleVentaDto::getProductoId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        Cliente cliente = dto.getClienteId() == null
                ? null
                : clienteRepository.findById(dto.getClienteId()).orElseThrow(() ->
                        new EntityNotFoundException("No existe un cliente con ID: " + dto.getClienteId()));

        Venta venta = Venta.builder()
                .fecha(LocalDateTime.now())
                .tipoComprobante(dto.getTipoComprobante())
                .formaPago(dto.getFormaPago())
                .cliente(cliente)
                .usuario(usuario)
                .caja(caja)
                .estadoVenta(dto.getFormaPago() == FormaPago.CUENTA_CORRIENTE
                        ? EstadoVenta.PENDIENTE
                        : EstadoVenta.PAGADA)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);

        for (DetalleVentaDto renglon : dto.getDetalles()) {

            Producto producto = productos.get(renglon.getProductoId());
            BigDecimal cantidad = CantidadUtils.normalizar(renglon.getCantidad());
            BigDecimal precio = producto.getPrecioVenta().setScale(2);
            BigDecimal importe = DetalleVenta.calcularSubtotal(cantidad, precio);

            venta.getDetalles().add(DetalleVenta.builder()
                    .venta(venta)
                    .producto(producto)
                    .cantidad(cantidad)
                    .precioUnitario(precio)
                    .subtotal(importe)
                    .build());

            subtotal = subtotal.add(importe);
        }

        BigDecimal descuento = dto.getDescuento().setScale(2);

        venta.setSubtotal(subtotal);
        venta.setDescuento(descuento);
        venta.setTotal(subtotal.subtract(descuento));

        return venta;
    }

    private List<LineaInventario> lineasDeInventario(Venta venta) {

        return venta.getDetalles().stream()
                .map(d -> new LineaInventario(d.getProducto().getId(), d.getCantidad()))
                .toList();
    }

}
