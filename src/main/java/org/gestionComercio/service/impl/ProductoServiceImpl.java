package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.ProductoDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.ProductoMapper;
import org.gestionComercio.repository.CategoriaRepository;
import org.gestionComercio.repository.MarcaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.ProveedorRepository;
import org.gestionComercio.service.ProductoService;
import org.gestionComercio.validation.ProductoValidator;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductoServiceImpl implements ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;
    private final ProveedorRepository proveedorRepository;

    private final ProductoMapper productoMapper;
    private final ProductoValidator productoValidator;

    @Override
    public ProductoDto save(ProductoDto dto) {

        validar(dto);

        normalizarStock(dto);

        Producto producto = productoMapper.toEntity(dto);

        cargarRelaciones(producto, dto);

        producto = productoRepository.save(producto);

        return productoMapper.toDto(producto);
    }

    @Override
    public ProductoDto update(ProductoDto dto) {

        validar(dto);

        normalizarStock(dto);

        Producto producto = buscarProducto(dto.getId());

        productoMapper.updateEntity(dto, producto);

        cargarRelaciones(producto, dto);

        producto = productoRepository.save(producto);

        return productoMapper.toDto(producto);
    }

    @Override
    public void delete(Long id) {

        Producto producto = buscarProducto(id);

        producto.setEstadoRegistro(EstadoRegistro.INACTIVO);

        productoRepository.save(producto);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<ProductoDto> findById(Long id) {

        return productoRepository.findById(id)
                .map(productoMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductoDto> findAll() {

        return productoRepository.findAll()
                .stream()
                .map(productoMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductoDto> search(String texto) {

        return productoRepository
                .findByNombreContainingIgnoreCase(texto)
                .stream()
                .map(productoMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public long count() {
        return productoRepository.count();
    }

    private void validar(ProductoDto dto) {

        ValidationResult result = productoValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result.getMessage());
        }
    }

    private void normalizarStock(ProductoDto dto) {

        if (!Boolean.TRUE.equals(dto.getManejaStock())) {
            dto.setStock(0);
            dto.setStockMinimo(0);
        }
    }

    private void cargarRelaciones(Producto producto, ProductoDto dto) {

        producto.setCategoria(buscarCategoria(dto.getCategoriaId()));
        producto.setMarca(buscarMarca(dto.getMarcaId()));
        producto.setProveedor(buscarProveedor(dto.getProveedorId()));
    }

    private Producto buscarProducto(Long id) {

        return productoRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "No existe un producto con ID: " + id));
    }

    private Categoria buscarCategoria(Long id) {

        return categoriaRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "No existe una categoría con ID: " + id));
    }

    private Marca buscarMarca(Long id) {

        return marcaRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "No existe una marca con ID: " + id));
    }

    private Proveedor buscarProveedor(Long id) {

        return proveedorRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException(
                                "No existe un proveedor con ID: " + id));
    }
}