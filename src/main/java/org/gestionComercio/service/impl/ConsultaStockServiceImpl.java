package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.inventario.ResumenStock;
import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.enums.FiltroStock;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ConsultaStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaStockServiceImpl implements ConsultaStockService {

    private final ProductoRepository productoRepository;
    private final SesionUsuario sesionUsuario;

    @Override
    public List<StockProductoDto> consultar(String texto, Long categoriaId, FiltroStock filtro) {

        sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_VER);

        String busqueda = texto == null ? "" : texto.trim();
        FiltroStock situacion = filtro == null ? FiltroStock.TODOS : filtro;

        return productoRepository.consultarStock(busqueda, categoriaId, situacion.name());
    }

    @Override
    public StockProductoDto consultarProducto(Long productoId) {

        sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_VER);

        if (productoId == null) {
            throw new BusinessException("El producto es obligatorio.");
        }

        return productoRepository.consultarStockDeProducto(productoId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No existe un producto con ID: " + productoId));
    }

    @Override
    public List<StockProductoDto> consultarProductosConMovimientos() {

        sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_VER);

        return productoRepository.consultarStockConMovimientos();
    }

    @Override
    public ResumenStock resumen() {

        sesionUsuario.requerirPermiso(PermisoCodigo.INVENTARIO_VER);

        return new ResumenStock(
                productoRepository.contarConControlDeStock(),
                productoRepository.contarConStockBajo(),
                productoRepository.contarSinStock());
    }
}
