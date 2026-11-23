package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.dto.configuracion.ConfiguracionDto;
import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.dto.venta.DetalleVentaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.entity.Venta;
import org.gestionComercio.enums.EstadoVenta;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.mapper.ClienteMapper;
import org.gestionComercio.mapper.EmpresaMapper;
import org.gestionComercio.mapper.VentaMapper;
import org.gestionComercio.report.ComprobanteVentaPdf;
import org.gestionComercio.report.OpcionesComprobante;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.VentaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ComprobanteVentaService;
import org.gestionComercio.service.ConfiguracionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Lee la empresa directamente del repositorio: imprimir un comprobante no
 * requiere el permiso de ver la configuración (EMPRESA_VER).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComprobanteVentaServiceImpl implements ComprobanteVentaService {

    private final VentaRepository ventaRepository;
    private final EmpresaRepository empresaRepository;
    private final VentaMapper ventaMapper;
    private final EmpresaMapper empresaMapper;
    private final ClienteMapper clienteMapper;
    private final ConfiguracionService configuracionService;
    private final SesionUsuario sesionUsuario;

    @Override
    public byte[] generarPdf(Long ventaId) {

        sesionUsuario.requerirAlgunPermiso(PermisoCodigo.VENTA_VER, PermisoCodigo.VENTA_CREAR);

        if (ventaId == null) {
            throw new BusinessException("La venta es obligatoria.");
        }

        Venta venta = ventaRepository.findConDetalles(ventaId)
                .orElseThrow(() -> new EntityNotFoundException("No existe una venta con ID: " + ventaId));

        ClienteDto cliente = venta.getCliente() == null ? null : clienteMapper.toDto(venta.getCliente());

        return ComprobanteVentaPdf.generar(ventaMapper.toDto(venta), empresa(), cliente,
                opciones(configuracionService.vigente()));
    }

    @Override
    public byte[] generarPrueba(ConfiguracionDto formato) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CONFIGURACION_VER);

        ConfiguracionDto configuracion = formato != null ? formato : configuracionService.vigente();

        return ComprobanteVentaPdf.generar(ventaDePrueba(), empresa(), null, opciones(configuracion));
    }

    private EmpresaDto empresa() {

        return empresaRepository.findFirstByOrderByIdAsc()
                .map(empresaMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No hay datos de la empresa cargados. Complételos en Configuración."));
    }

    static OpcionesComprobante opciones(ConfiguracionDto configuracion) {
        return new OpcionesComprobante(configuracion.getFormatoTicket(),
                !Boolean.FALSE.equals(configuracion.getImprimirLogo()), configuracion.getPieComprobante());
    }

    /** Un ticket con dos renglones de ejemplo, a nombre del usuario de la sesión. */
    private VentaDto ventaDePrueba() {

        List<DetalleVentaDto> detalles = new ArrayList<>();
        detalles.add(renglon("PRUEBA-1", "Producto de prueba", "2", "1500.00"));
        detalles.add(renglon("PRUEBA-2", "Otro producto con un nombre bastante más largo", "1", "2350.50"));

        BigDecimal subtotal = detalles.stream().map(DetalleVentaDto::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);

        String vendedor = sesionUsuario.getUsuario() == null ? null
                : sesionUsuario.getUsuario().getNombre() + " " + sesionUsuario.getUsuario().getApellido();

        return VentaDto.builder()
                .fecha(LocalDateTime.now())
                .tipoComprobante(TipoComprobante.TICKET)
                .numeroComprobante(0L)
                .clienteNombre(VentaDto.CONSUMIDOR_FINAL)
                .formaPago(FormaPago.EFECTIVO)
                .subtotal(subtotal)
                .descuento(BigDecimal.ZERO.setScale(2))
                .total(subtotal)
                .estadoVenta(EstadoVenta.PAGADA)
                .usuarioNombreCompleto(vendedor)
                .detalles(detalles)
                .build();
    }

    private static DetalleVentaDto renglon(String codigo, String nombre, String cantidad, String precio) {

        BigDecimal cant = new BigDecimal(cantidad);
        BigDecimal unitario = new BigDecimal(precio);

        return DetalleVentaDto.builder()
                .productoCodigo(codigo)
                .productoNombre(nombre)
                .cantidad(cant)
                .precioUnitario(unitario)
                .subtotal(cant.multiply(unitario).setScale(2))
                .build();
    }

}
