package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.entity.MovimientoInventario;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ConcurrencyException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.repository.MovimientoInventarioRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.util.CantidadUtils;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Núcleo que comparten {@link InventarioServiceImpl} (operaciones manuales) y
 * {@link InventarioComprobanteServiceImpl} (ventas y compras). Es la única
 * clase que guarda movimientos y cambia el stock de los productos.
 *
 * <p>No abre transacción propia: siempre corre dentro de la del servicio que
 * lo llama, así el movimiento y el stock se confirman o revierten juntos.
 */
@Component
@RequiredArgsConstructor
class RegistradorMovimientosInventario {

    private final ProductoRepository productoRepository;
    private final MovimientoInventarioRepository movimientoInventarioRepository;
    private final SesionUsuario sesionUsuario;

    /** Usuario de la sesión, responsable de los movimientos. */
    Usuario usuarioResponsable() {

        if (!sesionUsuario.estaAutenticado()) {
            throw new AuthorizationException("Debe iniciar sesión para modificar el stock.");
        }

        return sesionUsuario.getUsuario();
    }

    /**
     * Carga los productos indicados, en el mismo orden.
     *
     * @param exigirActivo true para rechazar productos dados de baja (operaciones nuevas)
     * @throws EntityNotFoundException si alguno no existe o no está activo
     */
    Map<Long, Producto> cargarProductos(Collection<Long> ids, boolean exigirActivo) {

        Map<Long, Producto> encontrados = productoRepository.findAllById(ids).stream()
                .filter(p -> !exigirActivo || p.getEstadoRegistro() == EstadoRegistro.ACTIVO)
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        List<Long> faltantes = ids.stream().filter(id -> !encontrados.containsKey(id)).toList();

        if (!faltantes.isEmpty()) {
            String lista = faltantes.stream().map(String::valueOf).collect(Collectors.joining(", "));
            throw new EntityNotFoundException(exigirActivo
                    ? (faltantes.size() == 1 ? "No existe un producto activo con ID: " : "No existen productos activos con ID: ") + lista
                    : (faltantes.size() == 1 ? "No existe un producto con ID: " : "No existen productos con ID: ") + lista);
        }

        Map<Long, Producto> ordenados = new LinkedHashMap<>();
        ids.forEach(id -> ordenados.put(id, encontrados.get(id)));

        return ordenados;
    }

    /**
     * Registra un lote de movimientos, como mucho uno por producto.
     *
     * <p>Primero crea y valida todos (datos, decimales, disponibilidad de
     * stock) e informa juntos todos los problemas; si hay alguno no guarda
     * nada. Después guarda los movimientos, aplica el nuevo stock a cada
     * producto y lo guarda con flush inmediato, para que un conflicto de
     * {@code @Version} se detecte acá y se informe como
     * {@link ConcurrencyException}.
     *
     * @param creadores cada uno crea un movimiento a partir del stock actual de su producto
     * @return los movimientos guardados, en el mismo orden
     */
    List<MovimientoInventario> registrar(List<Supplier<MovimientoInventario>> creadores) {

        List<String> errores = new ArrayList<>();
        List<MovimientoInventario> movimientos = new ArrayList<>();

        for (Supplier<MovimientoInventario> creador : creadores) {

            MovimientoInventario movimiento;

            try {
                movimiento = creador.get();
            } catch (IllegalArgumentException | NullPointerException e) {
                errores.add(e.getMessage());
                continue;
            }

            String sinStock = verificarDisponibilidad(movimiento);

            if (sinStock != null) {
                errores.add(sinStock);
            }

            movimientos.add(movimiento);
        }

        if (!errores.isEmpty()) {
            throw new BusinessException(errores.size() == 1
                    ? errores.get(0)
                    : "No se pudo registrar el movimiento de stock:\n• " + String.join("\n• ", errores));
        }

        Set<Producto> productos = Collections.newSetFromMap(new IdentityHashMap<>());

        for (MovimientoInventario movimiento : movimientos) {
            if (!productos.add(movimiento.getProducto())) {
                throw new IllegalStateException(
                        "Un lote no puede tener dos movimientos del mismo producto: agrupe las cantidades.");
            }
        }

        List<MovimientoInventario> guardados = new ArrayList<>();

        for (MovimientoInventario movimiento : movimientos) {
            MovimientoInventario guardado = movimientoInventarioRepository.save(movimiento);
            guardado.getProducto().aplicarMovimiento(guardado);
            guardados.add(guardado);
        }

        guardarProductos(new ArrayList<>(productos));

        return guardados;
    }

    private void guardarProductos(List<Producto> productos) {

        try {
            productoRepository.saveAllAndFlush(productos);
        } catch (OptimisticLockingFailureException e) {
            String quien = productos.size() == 1
                    ? "El stock de \"" + productos.get(0).getNombre() + "\""
                    : "El stock de uno o más productos";
            throw new ConcurrencyException(quien + " fue modificado por otra operación mientras se "
                    + "registraba el movimiento. No se guardaron cambios; intente nuevamente.", e);
        }
    }

    /**
     * Política de stock negativo: ningún movimiento puede dejar el stock por
     * debajo de cero, salvo que el producto lo permita explícitamente.
     *
     * @return el mensaje de error, o null si hay stock suficiente
     */
    private String verificarDisponibilidad(MovimientoInventario movimiento) {

        Producto producto = movimiento.getProducto();

        if (movimiento.getStockResultante().signum() >= 0
                || Boolean.TRUE.equals(producto.getPermiteStockNegativo())) {
            return null;
        }

        if (movimiento.getTipo() == TipoMovimientoInventario.SALIDA) {
            return "Stock insuficiente para \"" + producto.getNombre() + "\": disponible "
                    + CantidadUtils.formatear(movimiento.getStockAnterior()) + ", solicitado "
                    + CantidadUtils.formatear(movimiento.getCantidad().negate()) + ".";
        }

        return "El ajuste dejaría el stock de \"" + producto.getNombre() + "\" en "
                + CantidadUtils.formatear(movimiento.getStockResultante())
                + " y el producto no permite stock negativo.";
    }

}
