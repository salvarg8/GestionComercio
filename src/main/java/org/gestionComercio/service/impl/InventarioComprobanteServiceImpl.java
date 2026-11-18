package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.inventario.LineaInventario;
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.entity.MovimientoInventario;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.mapper.MovimientoInventarioMapper;
import org.gestionComercio.repository.MovimientoInventarioRepository;
import org.gestionComercio.service.InventarioComprobanteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class InventarioComprobanteServiceImpl implements InventarioComprobanteService {

    private final RegistradorMovimientosInventario registrador;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final MovimientoInventarioMapper movimientoInventarioMapper;

    @Override
    public List<MovimientoInventarioDto> registrarSalidas(
            OrigenMovimientoInventario origen, Long comprobanteId, List<LineaInventario> lineas, String motivo) {

        return registrar(origen, comprobanteId, lineas, motivo, TipoMovimientoInventario.SALIDA);
    }

    @Override
    public List<MovimientoInventarioDto> registrarEntradas(
            OrigenMovimientoInventario origen, Long comprobanteId, List<LineaInventario> lineas, String motivo) {

        return registrar(origen, comprobanteId, lineas, motivo, TipoMovimientoInventario.ENTRADA);
    }

    @Override
    public List<MovimientoInventarioDto> revertir(
            OrigenMovimientoInventario origen, Long comprobanteId, String motivo) {

        Usuario usuario = registrador.usuarioResponsable();
        validarComprobante(origen, comprobanteId);

        if (movimientoInventarioRepository.existsByOrigenAndOrigenIdAndMovimientoRevertidoIsNotNull(
                origen, comprobanteId)) {
            throw new BusinessException("Los movimientos de stock de " + describir(origen, comprobanteId)
                    + " ya fueron revertidos.");
        }

        List<MovimientoInventario> originales =
                movimientoInventarioRepository.findOriginalesDelComprobante(origen, comprobanteId);

        List<Supplier<MovimientoInventario>> creadores = new ArrayList<>();

        for (MovimientoInventario original : originales) {
            creadores.add(() -> MovimientoInventario.revertir(original, motivo, usuario));
        }

        return creadores.isEmpty()
                ? List.of()
                : movimientoInventarioMapper.toDtoList(registrador.registrar(creadores));
    }

    private List<MovimientoInventarioDto> registrar(
            OrigenMovimientoInventario origen,
            Long comprobanteId,
            List<LineaInventario> lineas,
            String motivo,
            TipoMovimientoInventario tipo) {

        Usuario usuario = registrador.usuarioResponsable();
        validarComprobante(origen, comprobanteId);

        Map<Long, BigDecimal> cantidades = agruparPorProducto(lineas);

        if (movimientoInventarioRepository.existsByOrigenAndOrigenIdAndMovimientoRevertidoIsNull(
                origen, comprobanteId)) {
            throw new BusinessException(describirConMayuscula(origen, comprobanteId)
                    + " ya registró sus movimientos de stock.");
        }

        Map<Long, Producto> productos = registrador.cargarProductos(cantidades.keySet(), true);

        List<Supplier<MovimientoInventario>> creadores = new ArrayList<>();

        cantidades.forEach((productoId, cantidad) -> {

            Producto producto = productos.get(productoId);

            if (!Boolean.TRUE.equals(producto.getManejaStock())) {
                return;
            }

            creadores.add(() -> MovimientoInventario.nuevo()
                    .producto(producto)
                    .tipo(tipo)
                    .cantidad(tipo == TipoMovimientoInventario.SALIDA ? cantidad.negate() : cantidad)
                    .motivo(motivo)
                    .origen(origen)
                    .origenId(comprobanteId)
                    .usuario(usuario)
                    .registrar());
        });

        return creadores.isEmpty()
                ? List.of()
                : movimientoInventarioMapper.toDtoList(registrador.registrar(creadores));
    }

    /** Suma los renglones del mismo producto, conservando el orden de aparición. */
    private Map<Long, BigDecimal> agruparPorProducto(List<LineaInventario> lineas) {

        if (lineas == null || lineas.isEmpty()) {
            throw new BusinessException("El comprobante no tiene renglones.");
        }

        Map<Long, BigDecimal> cantidades = new LinkedHashMap<>();

        for (LineaInventario linea : lineas) {

            if (linea == null || linea.productoId() == null) {
                throw new BusinessException("Cada renglón debe indicar el producto.");
            }

            if (linea.cantidad() == null || linea.cantidad().signum() <= 0) {
                throw new BusinessException("La cantidad de cada renglón debe ser mayor que cero.");
            }

            cantidades.merge(linea.productoId(), linea.cantidad(), BigDecimal::add);
        }

        return cantidades;
    }

    /** Son errores de programación del servicio que llama, no del usuario. */
    private void validarComprobante(OrigenMovimientoInventario origen, Long comprobanteId) {

        if (origen == null || !origen.requiereComprobante()) {
            throw new IllegalArgumentException(
                    "El origen debe ser un comprobante (venta o compra); para movimientos manuales use InventarioService.");
        }

        if (comprobanteId == null) {
            throw new IllegalArgumentException("El ID del comprobante es obligatorio.");
        }
    }

    private String describir(OrigenMovimientoInventario origen, Long comprobanteId) {
        return "la " + origen.getDescripcion().toLowerCase() + " N° " + comprobanteId;
    }

    private String describirConMayuscula(OrigenMovimientoInventario origen, Long comprobanteId) {
        return "La " + origen.getDescripcion().toLowerCase() + " N° " + comprobanteId;
    }

}
