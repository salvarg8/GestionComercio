package org.gestionComercio.service.impl;

import org.gestionComercio.dto.inventario.ResumenStock;
import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.FiltroStock;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.security.SesionUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaStockServiceImplTest {

    @Mock
    private ProductoRepository productoRepository;

    private SesionUsuario sesionUsuario;

    private ConsultaStockServiceImpl service;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        loguearCon(EnumSet.of(PermisoCodigo.INVENTARIO_VER));

        service = new ConsultaStockServiceImpl(productoRepository, sesionUsuario);
    }

    private void loguearCon(Set<PermisoCodigo> permisos) {
        sesionUsuario.iniciarSesion(Usuario.builder().usuario("ana").nombre("Ana").build(), permisos);
    }

    private static StockProductoDto stock(Long id) {
        return new StockProductoDto(id, "P" + id, null, "Producto " + id, 1L, "Almacén", "Marca",
                UnidadMedida.UNIDAD, BigDecimal.ONE, BigDecimal.ZERO, true, false, EstadoRegistro.ACTIVO);
    }

    @Test
    @DisplayName("Consulta con los filtros indicados, recortando el texto")
    void consultarConFiltros() {
        List<StockProductoDto> esperado = List.of(stock(1L));
        when(productoRepository.consultarStock("yerba", 3L, "SIN_STOCK")).thenReturn(esperado);

        List<StockProductoDto> resultado = service.consultar("  yerba ", 3L, FiltroStock.SIN_STOCK);

        assertSame(esperado, resultado);
    }

    @Test
    @DisplayName("Sin filtros: texto vacío, todas las categorías y todas las situaciones")
    void consultarSinFiltros() {
        when(productoRepository.consultarStock("", null, "TODOS")).thenReturn(List.of());

        service.consultar(null, null, null);

        verify(productoRepository).consultarStock("", null, "TODOS");
    }

    @Test
    @DisplayName("Consultar el stock requiere INVENTARIO_VER")
    void consultarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.PRODUCTO_VER));

        assertThrows(AuthorizationException.class, () -> service.consultar("", null, FiltroStock.TODOS));
        assertThrows(AuthorizationException.class, () -> service.consultarProducto(1L));
        assertThrows(AuthorizationException.class, () -> service.resumen());
        assertThrows(AuthorizationException.class, () -> service.consultarProductosConMovimientos());

        verifyNoInteractions(productoRepository);
    }

    @Test
    @DisplayName("Lista los productos con movimientos para el historial")
    void productosConMovimientos() {
        List<StockProductoDto> esperado = List.of(stock(1L), stock(2L));
        when(productoRepository.consultarStockConMovimientos()).thenReturn(esperado);

        assertSame(esperado, service.consultarProductosConMovimientos());
    }

    @Test
    @DisplayName("Devuelve el stock de un producto")
    void consultarProducto() {
        StockProductoDto esperado = stock(7L);
        when(productoRepository.consultarStockDeProducto(7L)).thenReturn(Optional.of(esperado));

        assertSame(esperado, service.consultarProducto(7L));
    }

    @Test
    @DisplayName("Falla si el producto no existe o no se indica")
    void consultarProductoInexistente() {
        when(productoRepository.consultarStockDeProducto(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.consultarProducto(99L));
        assertThrows(BusinessException.class, () -> service.consultarProducto(null));
    }

    @Test
    @DisplayName("El resumen junta los tres contadores")
    void resumen() {
        when(productoRepository.contarConControlDeStock()).thenReturn(10L);
        when(productoRepository.contarConStockBajo()).thenReturn(4L);
        when(productoRepository.contarSinStock()).thenReturn(1L);

        assertEquals(new ResumenStock(10, 4, 1), service.resumen());
    }
}
