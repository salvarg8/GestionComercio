package org.gestionComercio.validation;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.ProductoDto;
import org.gestionComercio.repository.ProductoRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductoValidator implements Validator<ProductoDto> {

    private final ProductoRepository productoRepository;

    @Override
    public ValidationResult validate(ProductoDto dto) {

        ValidationResult result = new ValidationResult();

        validarCodigo(dto, result);
        validarCodigoBarras(dto, result);
        validarNombre(dto, result);
        validarDescripcion(dto, result);
        validarPrecios(dto, result);
        validarStock(dto, result);
        validarRelaciones(dto, result);

        return result;
    }

    private void validarCodigo(ProductoDto dto, ValidationResult result) {

        ValidationUtils.required(dto.getCodigo(), "codigo", "El código es obligatorio.", result);

        if (dto.getCodigo() == null || dto.getCodigo().isBlank()) {
            return;
        }

        boolean existe = dto.getId() == null
                ? productoRepository.existsByCodigo(dto.getCodigo())
                : productoRepository.existsByCodigoAndIdNot(dto.getCodigo(), dto.getId());

        if (existe) {
            result.addError("codigo", "Ya existe un producto con ese código.");
        }
    }

    private void validarCodigoBarras(ProductoDto dto, ValidationResult result) {

        if (dto.getCodigoBarras() == null || dto.getCodigoBarras().isBlank()) {
            return;
        }

        boolean existe = dto.getId() == null
                ? productoRepository.existsByCodigoBarras(dto.getCodigo())
                : productoRepository.existsByCodigoBarrasAndIdNot(dto.getCodigoBarras(), dto.getId());

        if (existe) {
            result.addError("codigoBarras", "Ya existe un producto con ese código de barras.");
        }
    }

    private void validarNombre(ProductoDto dto, ValidationResult result) {

        ValidationUtils.required(dto.getNombre(), "nombre", "El nombre es obligatorio.", result);

        if (dto.getNombre() != null && dto.getNombre().length() > 100) {
            result.addError("nombre", "El nombre no puede superar los 100 caracteres.");
        }
    }

    private void validarDescripcion(ProductoDto dto, ValidationResult result) {

        if (dto.getDescripcion() != null && dto.getDescripcion().length() > 255) {
            result.addError("descripcion", "La descripción no puede superar los 255 caracteres.");
        }
    }

    private void validarPrecios(ProductoDto dto, ValidationResult result) {

        ValidationUtils.notNull(dto.getPrecioCompra(), "precioCompra", "El precio de compra es obligatorio.", result);
        ValidationUtils.notNull(dto.getPrecioVenta(), "precioVenta", "El precio de venta es obligatorio.", result);

        ValidationUtils.nonNegative(dto.getPrecioCompra(), "precioCompra", "El precio de compra debe ser mayor o igual a cero.", result);
        ValidationUtils.nonNegative(dto.getPrecioVenta(), "precioVenta", "El precio de venta debe ser mayor o igual a cero.", result);

        if (dto.getPrecioCompra() != null
                && dto.getPrecioVenta() != null
                && dto.getPrecioVenta().compareTo(dto.getPrecioCompra()) < 0) {

            result.addError(
                    "precioVenta",
                    "El precio de venta no puede ser menor al precio de compra.");
        }
    }

    private void validarStock(ProductoDto dto, ValidationResult result) {

        if (!Boolean.TRUE.equals(dto.getManejaStock())) {
            return;
        }

        ValidationUtils.notNull(dto.getStock(), "stock", "El stock es obligatorio.", result);
        ValidationUtils.notNull(dto.getStockMinimo(), "stockMinimo", "El stock mínimo es obligatorio.", result);

        ValidationUtils.nonNegative(dto.getStock(), "stock", "El stock debe ser mayor o igual a cero.", result);
        ValidationUtils.nonNegative(dto.getStockMinimo(), "stockMinimo", "El stock mínimo debe ser mayor o igual a cero.", result);
    }

    private void validarRelaciones(ProductoDto dto, ValidationResult result) {

        ValidationUtils.notNull(dto.getCategoriaId(), "categoriaId", "Debe seleccionar una categoría.", result);

        ValidationUtils.notNull(dto.getMarcaId(), "marcaId", "Debe seleccionar una marca.", result);

        ValidationUtils.notNull(dto.getProveedorId(), "proveedorId", "Debe seleccionar un proveedor.", result);

        ValidationUtils.notNull(dto.getUnidadMedida(), "unidadMedida", "Debe seleccionar una unidad de medida.", result);

        ValidationUtils.notNull(dto.getAlicuotaIVA(), "alicuotaIVA", "Debe seleccionar una alícuota de IVA.", result);
    }

}