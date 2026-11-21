package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.dto.compra.DetalleCompraDto;
import org.gestionComercio.dto.inventario.LineaInventario;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Compra;
import org.gestionComercio.entity.DetalleCompra;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.EstadoCompra;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.CompraMapper;
import org.gestionComercio.mapper.ProductoMapper;
import org.gestionComercio.mapper.ProveedorMapper;
import org.gestionComercio.repository.CajaRepository;
import org.gestionComercio.repository.CompraRepository;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.ProveedorRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.service.CompraService;
import org.gestionComercio.service.InventarioComprobanteService;
import org.gestionComercio.util.CantidadUtils;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.validation.CompraValidator;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cualquier excepción revierte la transacción completa: compra, stock, precios
 * y caja se confirman o se descartan juntos.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CompraServiceImpl implements CompraService {

    /** Largo de las columnas de texto (motivos y descripciones). */
    static final int TEXTO_MAX = 255;

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final InventarioComprobanteService inventarioComprobanteService;
    private final CompraValidator compraValidator;
    private final CompraMapper compraMapper;
    private final ProveedorMapper proveedorMapper;
    private final ProductoMapper productoMapper;
    private final SesionUsuario sesionUsuario;

    @Override
    public CompraDto registrar(CompraDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.COMPRA_CREAR);

        if (dto == null) {
            throw new BusinessException("Debe indicar los datos de la compra.");
        }

        normalizar(dto);

        ValidationResult result = compraValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }

        Usuario usuario = sesionUsuario.getUsuario();
        Compra compra = armarCompra(dto, usuario);

        // Primero la caja: si no se puede pagar, no se guarda nada.
        boolean pagaEnEfectivo = compra.getFormaPago() == FormaPago.EFECTIVO && compra.getTotal().signum() > 0;

        if (pagaEnEfectivo) {
            compra.setCaja(cajaParaPago(compra.getTotal(), usuario));
        }

        compra = compraRepository.save(compra);

        String referencia = referencia(compra);

        if (compra.getEstadoCompra() == EstadoCompra.RECIBIDA) {
            inventarioComprobanteService.registrarEntradas(
                    OrigenMovimientoInventario.COMPRA, compra.getId(), lineasDeInventario(compra), referencia);
        }

        if (dto.isActualizarPreciosCompra()) {
            compra.getDetalles().forEach(d -> d.getProducto().setPrecioCompra(d.getPrecioCompra()));
        }

        if (pagaEnEfectivo) {

            movimientoCajaRepository.save(MovimientoCaja.builder()
                    .fecha(LocalDateTime.now())
                    .tipo(TipoMovimientoCaja.PAGO_PROVEEDOR)
                    .descripcion(referencia)
                    .importe(compra.getTotal())
                    .caja(compra.getCaja())
                    .usuario(usuario)
                    .build());
        }

        return compraMapper.toDto(compra);
    }

    @Override
    public CompraDto recibir(Long compraId) {

        sesionUsuario.requerirPermiso(PermisoCodigo.COMPRA_CREAR);

        Compra compra = compraParaModificar(compraId);

        if (compra.getEstadoCompra() != EstadoCompra.PENDIENTE) {
            throw new BusinessException("Sólo se pueden recibir compras pendientes; la " + describir(compra)
                    + " está " + compra.getEstadoCompra().getDescripcion().toLowerCase() + ".");
        }

        // Ya bloqueada, se vuelve a leer con sus productos completos (no proxies
        // perezosos): el registro de inventario los compara por identidad.
        compra = compraRepository.findConDetalles(compraId).orElseThrow();

        inventarioComprobanteService.registrarEntradas(
                OrigenMovimientoInventario.COMPRA, compra.getId(), lineasDeInventario(compra), referencia(compra));

        compra.setEstadoCompra(EstadoCompra.RECIBIDA);

        return compraMapper.toDto(compraRepository.save(compra));
    }

    @Override
    public CompraDto anular(Long compraId, String motivo) {

        sesionUsuario.requerirPermiso(PermisoCodigo.COMPRA_ANULAR);

        if (compraId == null) {
            throw new BusinessException("La compra es obligatoria.");
        }

        String motivoAnulacion = validarMotivo(motivo);

        Compra compra = compraParaModificar(compraId);

        if (compra.getEstadoCompra() == EstadoCompra.ANULADA) {
            throw new BusinessException("La " + describir(compra) + " ya está anulada.");
        }

        if (compra.getEstadoCompra() != EstadoCompra.PENDIENTE && compra.getEstadoCompra() != EstadoCompra.RECIBIDA) {
            throw new BusinessException("Sólo se pueden anular compras pendientes o recibidas; la "
                    + describir(compra) + " está " + compra.getEstadoCompra().getDescripcion().toLowerCase() + ".");
        }

        Usuario usuario = sesionUsuario.getUsuario();

        // Primero la caja: si no hay dónde reintegrar el efectivo, no se toca nada.
        boolean reintegraEfectivo = compra.getCaja() != null;
        Caja caja = reintegraEfectivo ? cajaParaReintegro(compra, usuario) : null;

        if (compra.getEstadoCompra() == EstadoCompra.RECIBIDA) {
            inventarioComprobanteService.revertir(OrigenMovimientoInventario.COMPRA, compra.getId(),
                    recortar("Anulación de " + describir(compra) + ": " + motivoAnulacion));
        }

        LocalDateTime ahora = LocalDateTime.now();

        if (reintegraEfectivo) {

            movimientoCajaRepository.save(MovimientoCaja.builder()
                    .fecha(ahora)
                    .tipo(TipoMovimientoCaja.ANULACION_COMPRA)
                    .descripcion(recortar("Anulación de " + describir(compra)))
                    .importe(compra.getTotal())
                    .caja(caja)
                    .usuario(usuario)
                    .build());
        }

        compra.setEstadoCompra(EstadoCompra.ANULADA);
        compra.setFechaAnulacion(ahora);
        compra.setMotivoAnulacion(motivoAnulacion);
        compra.setUsuarioAnulacion(usuario);

        return compraMapper.toDto(compraRepository.save(compra));
    }

    @Transactional(readOnly = true)
    @Override
    public CompraDto obtener(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.COMPRA_VER);

        if (id == null) {
            throw new BusinessException("La compra es obligatoria.");
        }

        return compraRepository.findConDetalles(id)
                .map(compraMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("No existe una compra con ID: " + id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<CompraDto> listar(LocalDate desde, LocalDate hasta, EstadoCompra estado, Long proveedorId) {

        sesionUsuario.requerirPermiso(PermisoCodigo.COMPRA_VER);

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }

        return compraMapper.toResumenList(compraRepository.buscar(
                desde == null ? null : desde.atStartOfDay(),
                DateUtils.finDelDia(hasta),
                estado,
                proveedorId));
    }

    // ------------------------------------------------------------------
    // Consultas de la pantalla de compras
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    @Override
    public List<ProveedorDto> listarProveedores() {

        sesionUsuario.requerirAlgunPermiso(PermisoCodigo.COMPRA_CREAR, PermisoCodigo.COMPRA_VER);

        return proveedorMapper.toDtoList(
                proveedorRepository.findByEstadoRegistroOrderByRazonSocialAsc(EstadoRegistro.ACTIVO));
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductoDto> buscarProductos(String texto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.COMPRA_CREAR);

        return productoMapper.toDtoList(productoRepository.buscar(
                texto == null ? "" : texto.trim(), EstadoRegistro.ACTIVO));
    }

    // ------------------------------------------------------------------

    private Compra compraParaModificar(Long compraId) {

        if (compraId == null) {
            throw new BusinessException("La compra es obligatoria.");
        }

        return compraRepository.findParaModificar(compraId)
                .orElseThrow(() -> new EntityNotFoundException("No existe una compra con ID: " + compraId));
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

    /** "compra N° 12 (factura 0001-00001234)", o sin la factura si no tiene. */
    private static String describir(Compra compra) {
        return "compra N° " + compra.getId()
                + (compra.getNumeroFactura() == null ? "" : " (factura " + compra.getNumeroFactura() + ")");
    }

    /** "Compra N° 12 (factura 0001-00001234) - Proveedor", para movimientos de stock y caja. */
    private static String referencia(Compra compra) {
        return recortar("Compra N° " + compra.getId()
                + (compra.getNumeroFactura() == null ? "" : " (factura " + compra.getNumeroFactura() + ")")
                + " - " + compra.getProveedor().getRazonSocial());
    }

    /** Caja abierta del usuario donde se registra el pago; controla que su efectivo alcance. */
    private Caja cajaParaPago(BigDecimal total, Usuario usuario) {

        Caja caja = cajaRepository
                .findFirstByUsuarioIdAndEstadoCajaOrderByFechaAperturaDesc(usuario.getId(), EstadoCaja.ABIERTA)
                .orElseThrow(() -> new BusinessException("Para pagar la compra en efectivo debe tener una caja "
                        + "abierta. Abra la caja o elija otra forma de pago."));

        BigDecimal saldo = saldoDe(caja);

        if (total.compareTo(saldo) > 0) {
            throw new BusinessException("No hay efectivo suficiente en caja para pagar la compra: total "
                    + CurrencyUtils.format(total) + ", saldo en caja " + CurrencyUtils.format(saldo) + ".");
        }

        return caja;
    }

    /**
     * Caja donde se reintegra el efectivo de una compra anulada: la de la
     * compra si sigue abierta, o la caja abierta del usuario que anula.
     */
    private Caja cajaParaReintegro(Compra compra, Usuario usuario) {

        return compra.getCaja().getEstadoCaja() == EstadoCaja.ABIERTA
                ? compra.getCaja()
                : cajaRepository
                .findFirstByUsuarioIdAndEstadoCajaOrderByFechaAperturaDesc(usuario.getId(), EstadoCaja.ABIERTA)
                .orElseThrow(() -> new BusinessException("La compra se pagó en efectivo y su caja ya está "
                        + "cerrada. Abra una caja para registrar el reintegro del efectivo."));
    }

    private BigDecimal saldoDe(Caja caja) {

        return movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(caja.getId()).stream()
                .map(m -> m.getImporte().multiply(BigDecimal.valueOf(m.getTipo().getSigno())))
                .reduce(caja.getSaldoInicial(), BigDecimal::add);
    }

    /** Factura vacía = sin factura; sin estado = recibida; sin fecha = ahora; renglones nunca nulos. */
    private void normalizar(CompraDto dto) {

        if (dto.getNumeroFactura() != null) {
            String numero = dto.getNumeroFactura().trim();
            dto.setNumeroFactura(numero.isEmpty() ? null : numero);
        }

        if (dto.getEstadoCompra() == null) {
            dto.setEstadoCompra(EstadoCompra.RECIBIDA);
        }

        if (dto.getFecha() == null) {
            dto.setFecha(LocalDateTime.now());
        }

        if (dto.getDetalles() == null) {
            dto.setDetalles(new ArrayList<>());
        }
    }

    /** Arma la compra ya validada y calcula los totales. */
    private Compra armarCompra(CompraDto dto, Usuario usuario) {

        Map<Long, Producto> productos = productoRepository
                .findAllById(dto.getDetalles().stream().map(DetalleCompraDto::getProductoId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        Proveedor proveedor = proveedorRepository.findById(dto.getProveedorId()).orElseThrow(() ->
                new EntityNotFoundException("No existe un proveedor con ID: " + dto.getProveedorId()));

        Compra compra = Compra.builder()
                .fecha(dto.getFecha())
                .numeroFactura(dto.getNumeroFactura())
                .formaPago(dto.getFormaPago())
                .estadoCompra(dto.getEstadoCompra())
                .proveedor(proveedor)
                .usuario(usuario)
                .build();

        BigDecimal total = BigDecimal.ZERO.setScale(2);

        for (DetalleCompraDto renglon : dto.getDetalles()) {

            BigDecimal cantidad = CantidadUtils.normalizar(renglon.getCantidad());
            BigDecimal precio = renglon.getPrecioCompra().setScale(2);
            BigDecimal importe = DetalleCompra.calcularSubtotal(cantidad, precio);

            compra.getDetalles().add(DetalleCompra.builder()
                    .compra(compra)
                    .producto(productos.get(renglon.getProductoId()))
                    .cantidad(cantidad)
                    .precioCompra(precio)
                    .subtotal(importe)
                    .build());

            total = total.add(importe);
        }

        compra.setSubtotal(total);
        compra.setTotal(total);

        return compra;
    }

    private List<LineaInventario> lineasDeInventario(Compra compra) {

        return compra.getDetalles().stream()
                .map(d -> new LineaInventario(d.getProducto().getId(), d.getCantidad()))
                .toList();
    }

}
