package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.producto.ProductoDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FiltroEstado;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.ProductoMapper;
import org.gestionComercio.repository.CategoriaRepository;
import org.gestionComercio.repository.MarcaRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.repository.ProveedorRepository;
import org.gestionComercio.security.SesionUsuario;
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
    private final SesionUsuario sesionUsuario;

    /** El producto nuevo arranca activo y con stock 0: el stock entra por movimientos. */
    @Override
    public ProductoDto save(ProductoDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_CREAR);

        normalizar(dto);
        validar(dto);

        Producto producto = productoMapper.toEntity(dto);
        producto.setId(null);
        producto.setEstadoRegistro(EstadoRegistro.ACTIVO);
        producto.setStock(0);

        cargarRelaciones(producto, dto);

        return productoMapper.toDto(productoRepository.save(producto));
    }

    /**
     * Se puede editar un producto activo o dado de baja (por ejemplo, para
     * asignarle una categoría vigente antes de habilitarlo). No cambia su estado
     * ni su stock.
     */
    @Override
    public ProductoDto update(ProductoDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_EDITAR);

        normalizar(dto);
        validar(dto);

        Producto producto = buscar(dto.getId());

        productoMapper.updateEntity(dto, producto);

        cargarRelaciones(producto, dto);

        return productoMapper.toDto(productoRepository.save(producto));
    }

    /**
     * Baja lógica: el producto sigue en la base, así que las ventas, compras y
     * movimientos de stock que lo referencian se conservan. Ya no aparece en los
     * listados de activos ni se puede usar en operaciones nuevas.
     */
    @Override
    public void darDeBaja(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_DAR_BAJA);

        Producto producto = buscar(id, EstadoRegistro.ACTIVO);

        producto.setEstadoRegistro(EstadoRegistro.INACTIVO);

        productoRepository.save(producto);
    }

    /**
     * Una categoría sólo se puede eliminar si no tiene productos activos, así que
     * un producto dado de baja puede haber quedado con una categoría eliminada:
     * en ese caso no se habilita hasta que se le asigne otra. La marca y el
     * proveedor dados de baja, en cambio, se conservan.
     */
    @Override
    public void habilitar(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_HABILITAR);

        Producto producto = buscar(id, EstadoRegistro.INACTIVO);

        Categoria categoria = producto.getCategoria();

        if (categoria == null || categoria.getEstadoRegistro() != EstadoRegistro.ACTIVO) {
            throw new BusinessException(
                    "No se puede habilitar el producto \"" + producto.getNombre()
                            + "\" porque su categoría fue eliminada. Edítelo y asígnele otra categoría.");
        }

        producto.setEstadoRegistro(EstadoRegistro.ACTIVO);

        productoRepository.save(producto);
    }

    @Override
    public void delete(Long id) {
        darDeBaja(id);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<ProductoDto> findById(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_VER);

        return productoRepository.findById(id)
                .map(productoMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductoDto> findAll() {
        return buscar(null, FiltroEstado.ACTIVOS);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductoDto> search(String texto) {
        return buscar(texto, FiltroEstado.ACTIVOS);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ProductoDto> buscar(String texto, FiltroEstado filtro) {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_VER);

        String busqueda = texto == null ? "" : texto.trim();

        EstadoRegistro estado = switch (filtro == null ? FiltroEstado.ACTIVOS : filtro) {
            case ACTIVOS -> EstadoRegistro.ACTIVO;
            case DADOS_DE_BAJA -> EstadoRegistro.INACTIVO;
            case TODOS -> null;
        };

        return productoRepository.buscar(busqueda, estado).stream()
                .map(productoMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public long count() {

        sesionUsuario.requerirPermiso(PermisoCodigo.PRODUCTO_VER);

        return productoRepository.countByEstadoRegistro(EstadoRegistro.ACTIVO);
    }

    private void validar(ProductoDto dto) {

        ValidationResult result = productoValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }
    }

    /**
     * Recorta espacios, deja en null los opcionales vacíos y completa los valores
     * por defecto de los indicadores de stock.
     */
    private void normalizar(ProductoDto dto) {

        dto.setCodigo(recortar(dto.getCodigo()));
        dto.setNombre(recortar(dto.getNombre()));
        dto.setCodigoBarras(opcional(dto.getCodigoBarras()));
        dto.setDescripcion(opcional(dto.getDescripcion()));

        if (dto.getManejaStock() == null) {
            dto.setManejaStock(true);
        }

        if (dto.getPermiteStockNegativo() == null) {
            dto.setPermiteStockNegativo(false);
        }

        if (!dto.getManejaStock()) {
            dto.setStockMinimo(0);
            dto.setPermiteStockNegativo(false);
        }
    }

    private String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private String opcional(String valor) {

        if (valor == null) {
            return null;
        }

        String recortado = valor.trim();

        return recortado.isEmpty() ? null : recortado;
    }

    /*
     * El validador ya informó por campo si alguna relación no es válida; estas
     * búsquedas son la red de seguridad por si cambió algo entre medio.
     */
    private void cargarRelaciones(Producto producto, ProductoDto dto) {

        producto.setCategoria(buscarCategoria(dto.getCategoriaId()));
        producto.setMarca(buscarMarca(dto.getMarcaId(), producto.getMarca()));
        producto.setProveedor(buscarProveedor(dto.getProveedorId(), producto.getProveedor()));
    }

    private Producto buscar(Long id) {

        return productoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No existe un producto con ID: " + id));
    }

    private Producto buscar(Long id, EstadoRegistro estado) {

        return productoRepository.findByIdAndEstadoRegistro(id, estado)
                .orElseThrow(() -> new EntityNotFoundException(
                        estado == EstadoRegistro.ACTIVO
                                ? "No existe un producto activo con ID: " + id
                                : "No existe un producto dado de baja con ID: " + id));
    }

    private Categoria buscarCategoria(Long id) {

        return categoriaRepository.findByIdAndEstadoRegistro(id, EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No existe una categoría activa con ID: " + id));
    }

    /**
     * Sólo se pueden asignar marcas activas, salvo que el producto ya tuviera
     * esa marca: una marca dada de baja no obliga a cambiarla al editar el producto.
     */
    private Marca buscarMarca(Long id, Marca marcaActual) {

        if (marcaActual != null && id != null && id.equals(marcaActual.getId())) {
            return marcaActual;
        }

        return marcaRepository.findByIdAndEstadoRegistro(id, EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No existe una marca activa con ID: " + id));
    }

    /**
     * Sólo se pueden asignar proveedores activos, salvo que el producto ya tuviera
     * ese proveedor: uno dado de baja no obliga a cambiarlo al editar el producto.
     */
    private Proveedor buscarProveedor(Long id, Proveedor proveedorActual) {

        if (proveedorActual != null && id != null && id.equals(proveedorActual.getId())) {
            return proveedorActual;
        }

        return proveedorRepository.findByIdAndEstadoRegistro(id, EstadoRegistro.ACTIVO)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No existe un proveedor activo con ID: " + id));
    }
}
