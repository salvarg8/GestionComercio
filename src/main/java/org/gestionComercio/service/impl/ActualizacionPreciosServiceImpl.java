package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.producto.ActualizacionPrecioDto;
import org.gestionComercio.dto.producto.FiltroActualizacionPrecios;
import org.gestionComercio.dto.producto.PrecioActualizadoDto;
import org.gestionComercio.entity.ActualizacionPrecio;
import org.gestionComercio.entity.ActualizacionPrecioDetalle;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.Redondeo;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.repository.ActualizacionPrecioRepository;
import org.gestionComercio.repository.CategoriaRepository;
import org.gestionComercio.repository.MarcaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.ProveedorRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ActualizacionPreciosService;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ActualizacionPreciosServiceImpl implements ActualizacionPreciosService {

    /** Rebaja máxima: un precio no puede quedar en cero o negativo por el porcentaje. */
    static final BigDecimal PORCENTAJE_MINIMO = new BigDecimal("-100");

    /** Aumento máximo de una sola vez, para frenar errores de tipeo (ej.: 1000 en vez de 10). */
    static final BigDecimal PORCENTAJE_MAXIMO = new BigDecimal("1000");

    /** Mayor precio que admite la columna numeric(12, 2). */
    static final BigDecimal PRECIO_MAXIMO = new BigDecimal("9999999999.99");

    /** Actualizaciones que muestra el historial. */
    static final int HISTORIAL_MAX = 100;

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final ProductoRepository productoRepository;
    private final ActualizacionPrecioRepository actualizacionPrecioRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;
    private final ProveedorRepository proveedorRepository;
    private final SesionUsuario sesionUsuario;

    @Transactional(readOnly = true)
    @Override
    public List<PrecioActualizadoDto> previsualizar(FiltroActualizacionPrecios filtro) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_ACTUALIZAR_PRECIOS);

        validar(filtro);

        return productos(filtro).stream()
                .map(p -> propuesta(p, filtro))
                .toList();
    }

    @Override
    public ActualizacionPrecioDto aplicar(FiltroActualizacionPrecios filtro, List<PrecioActualizadoDto> vistaPrevia) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_ACTUALIZAR_PRECIOS);

        validar(filtro);

        List<Producto> productos = productos(filtro);

        if (productos.isEmpty()) {
            throw new BusinessException("No hay productos activos que cumplan el filtro.");
        }

        List<PrecioActualizadoDto> propuestas = productos.stream().map(p -> propuesta(p, filtro)).toList();

        if (!coincide(propuestas, vistaPrevia)) {
            throw new BusinessException("Desde que se generó la vista previa cambiaron los productos o sus "
                    + "precios. Vuelva a generar la vista previa antes de aplicar.");
        }

        Usuario usuario = sesionUsuario.getUsuario();

        ActualizacionPrecio actualizacion = ActualizacionPrecio.builder()
                .porcentaje(filtro.porcentaje().setScale(2, RoundingMode.HALF_UP))
                .precio(filtro.precio())
                .redondeo(filtro.redondeo())
                .categoria(filtro.categoriaId() == null ? null : categoriaRepository.getReferenceById(filtro.categoriaId()))
                .marca(filtro.marcaId() == null ? null : marcaRepository.getReferenceById(filtro.marcaId()))
                .proveedor(filtro.proveedorId() == null ? null : proveedorRepository.getReferenceById(filtro.proveedorId()))
                .filtro(descripcionFiltro(filtro))
                .cantidadProductos(productos.size())
                .build();

        for (int i = 0; i < productos.size(); i++) {

            Producto producto = productos.get(i);
            PrecioActualizadoDto propuesta = propuestas.get(i);

            actualizacion.getDetalles().add(ActualizacionPrecioDetalle.builder()
                    .actualizacion(actualizacion)
                    .producto(producto)
                    .precioCompraAnterior(propuesta.precioCompraAnterior())
                    .precioCompraNuevo(propuesta.precioCompraNuevo())
                    .precioVentaAnterior(propuesta.precioVentaAnterior())
                    .precioVentaNuevo(propuesta.precioVentaNuevo())
                    .build());

            producto.setPrecioCompra(propuesta.precioCompraNuevo());
            producto.setPrecioVenta(propuesta.precioVentaNuevo());
        }

        // El @Version de cada producto rechaza el guardado si otra PC lo modificó mientras tanto.
        productoRepository.saveAll(productos);

        return toDto(actualizacionPrecioRepository.save(actualizacion), usuario);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ActualizacionPrecioDto> historial() {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_ACTUALIZAR_PRECIOS);

        return actualizacionPrecioRepository.buscarRecientes(PageRequest.of(0, HISTORIAL_MAX)).stream()
                .map(a -> toDto(a, a.getUsuarioCreacion()))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<PrecioActualizadoDto> detalle(Long actualizacionId) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_ACTUALIZAR_PRECIOS);

        if (actualizacionId == null) {
            throw new BusinessException("La actualización es obligatoria.");
        }

        ActualizacionPrecio actualizacion = actualizacionPrecioRepository.findConDetalles(actualizacionId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No existe una actualización de precios con ID: " + actualizacionId));

        return actualizacion.getDetalles().stream()
                .sorted(Comparator.comparing((ActualizacionPrecioDetalle d) -> d.getProducto().getNombre())
                        .thenComparing(ActualizacionPrecioDetalle::getId))
                .map(d -> new PrecioActualizadoDto(
                        d.getProducto().getId(),
                        d.getProducto().getCodigo(),
                        d.getProducto().getNombre(),
                        null, null, null,
                        d.getPrecioCompraAnterior(),
                        d.getPrecioCompraNuevo(),
                        d.getPrecioVentaAnterior(),
                        d.getPrecioVentaNuevo()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Cálculo
    // ------------------------------------------------------------------

    private List<Producto> productos(FiltroActualizacionPrecios filtro) {
        return productoRepository.buscarParaActualizarPrecios(
                filtro.categoriaId(), filtro.marcaId(), filtro.proveedorId());
    }

    private static PrecioActualizadoDto propuesta(Producto producto, FiltroActualizacionPrecios filtro) {

        BigDecimal compra = escala(producto.getPrecioCompra());
        BigDecimal venta = escala(producto.getPrecioVenta());

        BigDecimal compraNueva = filtro.precio().incluyeCompra()
                ? precioNuevo(compra, filtro.porcentaje(), filtro.redondeo()) : compra;
        BigDecimal ventaNueva = filtro.precio().incluyeVenta()
                ? precioNuevo(venta, filtro.porcentaje(), filtro.redondeo()) : venta;

        if (compraNueva.compareTo(PRECIO_MAXIMO) > 0 || ventaNueva.compareTo(PRECIO_MAXIMO) > 0) {
            throw new BusinessException("El precio nuevo de \"" + producto.getNombre()
                    + "\" supera el máximo permitido. Revise el porcentaje.");
        }

        return new PrecioActualizadoDto(
                producto.getId(),
                producto.getCodigo(),
                producto.getNombre(),
                producto.getCategoria().getNombre(),
                producto.getMarca().getNombre(),
                producto.getProveedor().getRazonSocial(),
                compra, compraNueva,
                venta, ventaNueva);
    }

    /**
     * Precio más el porcentaje, redondeado al múltiplo más cercano. Un precio
     * positivo nunca queda en cero por el redondeo: en ese caso se redondea
     * hacia arriba. Un precio en cero queda en cero.
     */
    static BigDecimal precioNuevo(BigDecimal precio, BigDecimal porcentaje, Redondeo redondeo) {

        BigDecimal calculado = precio.multiply(CIEN.add(porcentaje)).divide(CIEN, 4, RoundingMode.HALF_UP);
        BigDecimal redondeado = redondeo.alMasCercano(calculado);

        if (redondeado.signum() == 0 && calculado.signum() > 0) {
            redondeado = redondeo.haciaArriba(calculado);
        }

        return redondeado;
    }

    private static BigDecimal escala(BigDecimal precio) {
        return Objects.requireNonNullElse(precio, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Mismos productos, en el mismo orden, con los mismos precios actuales y nuevos. */
    private static boolean coincide(List<PrecioActualizadoDto> actuales, List<PrecioActualizadoDto> previstos) {

        if (previstos == null || previstos.size() != actuales.size()) {
            return false;
        }

        for (int i = 0; i < actuales.size(); i++) {

            PrecioActualizadoDto a = actuales.get(i);
            PrecioActualizadoDto p = previstos.get(i);

            if (p == null
                    || !Objects.equals(a.productoId(), p.productoId())
                    || !igual(a.precioCompraAnterior(), p.precioCompraAnterior())
                    || !igual(a.precioCompraNuevo(), p.precioCompraNuevo())
                    || !igual(a.precioVentaAnterior(), p.precioVentaAnterior())
                    || !igual(a.precioVentaNuevo(), p.precioVentaNuevo())) {
                return false;
            }
        }

        return true;
    }

    private static boolean igual(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    // ------------------------------------------------------------------
    // Validación y textos
    // ------------------------------------------------------------------

    private void validar(FiltroActualizacionPrecios filtro) {

        if (filtro == null) {
            throw new BusinessException("Debe indicar el porcentaje y los productos a actualizar.");
        }

        ValidationResult result = new ValidationResult();

        BigDecimal porcentaje = filtro.porcentaje();

        if (porcentaje == null) {
            result.addError("porcentaje", "Ingrese el porcentaje de aumento o de rebaja.");
        } else if (porcentaje.signum() == 0) {
            result.addError("porcentaje", "El porcentaje no puede ser cero.");
        } else if (porcentaje.compareTo(PORCENTAJE_MINIMO) <= 0) {
            result.addError("porcentaje", "La rebaja tiene que ser menor al 100 %.");
        } else if (porcentaje.compareTo(PORCENTAJE_MAXIMO) > 0) {
            result.addError("porcentaje", "El aumento no puede superar el " + PORCENTAJE_MAXIMO + " %.");
        } else if (porcentaje.scale() > 2 && porcentaje.stripTrailingZeros().scale() > 2) {
            result.addError("porcentaje", "El porcentaje admite hasta dos decimales.");
        }

        if (filtro.precio() == null) {
            result.addError("precio", "Elija qué precio se actualiza.");
        }

        if (filtro.categoriaId() != null && categoriaRepository.findById(filtro.categoriaId()).isEmpty()) {
            result.addError("categoriaId", "La categoría elegida no existe.");
        }

        if (filtro.marcaId() != null && marcaRepository.findById(filtro.marcaId()).isEmpty()) {
            result.addError("marcaId", "La marca elegida no existe.");
        }

        if (filtro.proveedorId() != null && proveedorRepository.findById(filtro.proveedorId()).isEmpty()) {
            result.addError("proveedorId", "El proveedor elegido no existe.");
        }

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }
    }

    /** "Categoría: Bebidas · Marca: X", o "Todos los productos" sin filtros. */
    private String descripcionFiltro(FiltroActualizacionPrecios filtro) {

        List<String> partes = new ArrayList<>();

        if (filtro.categoriaId() != null) {
            categoriaRepository.findById(filtro.categoriaId()).map(Categoria::getNombre)
                    .ifPresent(n -> partes.add("Categoría: " + n));
        }

        if (filtro.marcaId() != null) {
            marcaRepository.findById(filtro.marcaId()).map(Marca::getNombre)
                    .ifPresent(n -> partes.add("Marca: " + n));
        }

        if (filtro.proveedorId() != null) {
            proveedorRepository.findById(filtro.proveedorId()).map(Proveedor::getRazonSocial)
                    .ifPresent(n -> partes.add("Proveedor: " + n));
        }

        String texto = partes.isEmpty() ? "Todos los productos" : String.join(" · ", partes);

        return texto.length() <= 255 ? texto : texto.substring(0, 254) + "…";
    }

    private static ActualizacionPrecioDto toDto(ActualizacionPrecio a, Usuario usuario) {

        return new ActualizacionPrecioDto(
                a.getId(),
                a.getFechaCreacion(),
                usuario == null ? null : (usuario.getNombre() + " " + usuario.getApellido()).trim(),
                a.getPorcentaje(),
                a.getPrecio(),
                a.getRedondeo(),
                a.getFiltro(),
                a.getCantidadProductos() == null ? 0 : a.getCantidadProductos());
    }
}
