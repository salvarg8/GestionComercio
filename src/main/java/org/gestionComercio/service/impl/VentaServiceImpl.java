package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.inventario.LineaInventario;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.entity.DetalleVenta;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.entity.Venta;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.VentaMapper;
import org.gestionComercio.repository.CajaRepository;
import org.gestionComercio.repository.ClienteRepository;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.VentaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.InventarioComprobanteService;
import org.gestionComercio.service.VentaService;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.validation.ValidationResult;
import org.gestionComercio.validation.VentaValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final InventarioComprobanteService inventarioComprobanteService;
    private final VentaValidator ventaValidator;
    private final VentaMapper ventaMapper;
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

        venta = ventaRepository.save(venta);

        String referencia = "Venta N° " + venta.getId();

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
    public List<VentaDto> listar(LocalDate desde, LocalDate hasta) {

        sesionUsuario.requerirPermiso(PermisoCodigo.VENTA_VER);

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }

        return ventaMapper.toResumenList(ventaRepository.buscar(
                desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.atTime(LocalTime.MAX)));
    }

    /** Descuento vacío = sin descuento; lista de renglones nunca nula. */
    private void normalizar(VentaDto dto) {

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
