package org.gestionComercio.validation;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.repository.CategoriaRepository;
import org.gestionComercio.repository.MarcaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.ProveedorRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Valida un producto ya normalizado por el servicio (textos recortados y
 * opcionales vacíos en null).
 */
@Component
@RequiredArgsConstructor
public class ProductoValidator implements Validator<ProductoDto> {

    /* Coinciden con los largos de las columnas de la entidad Producto. */
    public static final int CODIGO_MAX = 50;
    public static final int CODIGO_BARRAS_MAX = 20;
    public static final int NOMBRE_MAX = 150;
    public static final int DESCRIPCION_MAX = 500;

    /* Columnas de precio: precision 12, scale 2. */
    public static final int PRECIO_DECIMALES_MAX = 2;
    public static final int PRECIO_ENTEROS_MAX = 10;

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;
    private final ProveedorRepository proveedorRepository;

    @Override
    public ValidationResult validate(ProductoDto dto) {

        ValidationResult result = new ValidationResult();

        validarCodigo(dto, result);
        validarCodigoBarras(dto, result);
        validarNombre(dto, result);
        validarDescripcion(dto, result);
        validarPrecios(dto, result);
        validarStock(dto, result);
        validarClasificacionFiscal(dto, result);
        validarRelaciones(dto, result);

        return result;
    }

    private void validarCodigo(ProductoDto dto, ValidationResult result) {

        ValidationUtils.required(dto.getCodigo(), "codigo", "El código interno es obligatorio.", result);

        if (dto.getCodigo() == null || dto.getCodigo().isBlank()) {
            return;
        }

        if (dto.getCodigo().length() > CODIGO_MAX) {
            result.addError("codigo",
                    "El código interno no puede superar los " + CODIGO_MAX + " caracteres.");
        }

        Optional<Producto> existente = dto.getId() == null
                ? productoRepository.findFirstByCodigoIgnoreCase(dto.getCodigo())
                : productoRepository.findFirstByCodigoIgnoreCaseAndIdNot(dto.getCodigo(), dto.getId());

        existente.ifPresent(producto -> result.addError(
                "codigo",
                producto.getEstadoRegistro() == EstadoRegistro.ACTIVO
                        ? "Ya existe un producto con ese código interno."
                        : "Ya existe un producto dado de baja con ese código interno. Puede volver a habilitarlo desde el listado de productos."
        ));
    }

    private void validarCodigoBarras(ProductoDto dto, ValidationResult result) {

        if (dto.getCodigoBarras() == null || dto.getCodigoBarras().isBlank()) {
            return;
        }

        if (dto.getCodigoBarras().length() > CODIGO_BARRAS_MAX) {
            result.addError("codigoBarras",
                    "El código de barras no puede superar los " + CODIGO_BARRAS_MAX + " caracteres.");
        }

        if (dto.getCodigoBarras().chars().anyMatch(Character::isWhitespace)) {
            result.addError("codigoBarras", "El código de barras no puede contener espacios.");
        }

        Optional<Producto> existente = dto.getId() == null
                ? productoRepository.findByCodigoBarras(dto.getCodigoBarras())
                : productoRepository.findByCodigoBarrasAndIdNot(dto.getCodigoBarras(), dto.getId());

        existente.ifPresent(producto -> result.addError(
                "codigoBarras",
                producto.getEstadoRegistro() == EstadoRegistro.ACTIVO
                        ? "Ya existe un producto con ese código de barras (" + producto.getNombre() + ")."
                        : "Ya existe un producto dado de baja con ese código de barras (" + producto.getNombre() + ")."
        ));
    }

    private void validarNombre(ProductoDto dto, ValidationResult result) {

        ValidationUtils.required(dto.getNombre(), "nombre", "El nombre es obligatorio.", result);

        if (dto.getNombre() != null && dto.getNombre().length() > NOMBRE_MAX) {
            result.addError("nombre",
                    "El nombre no puede superar los " + NOMBRE_MAX + " caracteres.");
        }
    }

    private void validarDescripcion(ProductoDto dto, ValidationResult result) {

        if (dto.getDescripcion() != null && dto.getDescripcion().length() > DESCRIPCION_MAX) {
            result.addError("descripcion",
                    "La descripción no puede superar los " + DESCRIPCION_MAX + " caracteres.");
        }
    }

    private void validarPrecios(ProductoDto dto, ValidationResult result) {

        boolean compraValido = validarPrecio(dto.getPrecioCompra(), "precioCompra", "precio de compra", result);
        boolean ventaValido = validarPrecio(dto.getPrecioVenta(), "precioVenta", "precio de venta", result);

        if (compraValido && ventaValido
                && dto.getPrecioVenta().compareTo(dto.getPrecioCompra()) < 0) {

            result.addError("precioVenta",
                    "El precio de venta no puede ser menor al precio de compra.");
        }
    }

    /** @return true si el precio no tiene errores. */
    private boolean validarPrecio(BigDecimal precio, String campo, String nombre, ValidationResult result) {

        if (precio == null) {
            result.addError(campo, "El " + nombre + " es obligatorio.");
            return false;
        }

        if (precio.signum() < 0) {
            result.addError(campo, "El " + nombre + " no puede ser negativo.");
            return false;
        }

        BigDecimal sinCeros = precio.stripTrailingZeros();

        if (sinCeros.scale() > PRECIO_DECIMALES_MAX) {
            result.addError(campo,
                    "El " + nombre + " no puede tener más de " + PRECIO_DECIMALES_MAX + " decimales.");
            return false;
        }

        if (sinCeros.precision() - sinCeros.scale() > PRECIO_ENTEROS_MAX) {
            result.addError(campo, "El " + nombre + " es demasiado grande.");
            return false;
        }

        return true;
    }

    private void validarStock(ProductoDto dto, ValidationResult result) {

        if (!Boolean.TRUE.equals(dto.getManejaStock())) {
            return;
        }

        if (dto.getStockMinimo() == null) {
            result.addError("stockMinimo", "El stock mínimo es obligatorio.");
        } else if (dto.getStockMinimo() < 0) {
            result.addError("stockMinimo", "El stock mínimo no puede ser negativo.");
        }
    }

    private void validarClasificacionFiscal(ProductoDto dto, ValidationResult result) {

        ValidationUtils.notNull(dto.getUnidadMedida(), "unidadMedida",
                "Debe seleccionar una unidad de medida.", result);

        ValidationUtils.notNull(dto.getAlicuotaIVA(), "alicuotaIVA",
                "Debe seleccionar una alícuota de IVA.", result);
    }

    /**
     * La categoría tiene que estar vigente siempre. La marca y el proveedor
     * también, salvo que el producto ya los tuviera asignados: una marca o un
     * proveedor dados de baja no obligan a cambiarlos al editar el producto.
     */
    private void validarRelaciones(ProductoDto dto, ValidationResult result) {

        Producto actual = dto.getId() == null
                ? null
                : productoRepository.findById(dto.getId()).orElse(null);

        if (dto.getCategoriaId() == null) {
            result.addError("categoriaId", "Debe seleccionar una categoría.");
        } else if (categoriaRepository
                .findByIdAndEstadoRegistro(dto.getCategoriaId(), EstadoRegistro.ACTIVO)
                .isEmpty()) {
            result.addError("categoriaId", "La categoría seleccionada no existe o fue eliminada.");
        }

        if (dto.getMarcaId() == null) {
            result.addError("marcaId", "Debe seleccionar una marca.");
        } else if (!esMarcaActual(actual, dto.getMarcaId())
                && marcaRepository
                .findByIdAndEstadoRegistro(dto.getMarcaId(), EstadoRegistro.ACTIVO)
                .isEmpty()) {
            result.addError("marcaId", "La marca seleccionada no existe o fue dada de baja.");
        }

        if (dto.getProveedorId() == null) {
            result.addError("proveedorId", "Debe seleccionar un proveedor.");
        } else if (!esProveedorActual(actual, dto.getProveedorId())
                && proveedorRepository
                .findByIdAndEstadoRegistro(dto.getProveedorId(), EstadoRegistro.ACTIVO)
                .isEmpty()) {
            result.addError("proveedorId", "El proveedor seleccionado no existe o fue dado de baja.");
        }
    }

    private boolean esMarcaActual(Producto actual, Long marcaId) {
        return actual != null
                && actual.getMarca() != null
                && Objects.equals(actual.getMarca().getId(), marcaId);
    }

    private boolean esProveedorActual(Producto actual, Long proveedorId) {
        return actual != null
                && actual.getProveedor() != null
                && Objects.equals(actual.getProveedor().getId(), proveedorId);
    }
}
