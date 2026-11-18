package org.gestionComercio.service.impl;

import org.gestionComercio.dto.inventario.FiltroMovimientos;
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.entity.MovimientoInventario;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.MotivoAjusteInventario;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ConcurrencyException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.mapper.MovimientoInventarioMapper;
import org.gestionComercio.repository.MovimientoInventarioRepository;
import org.gestionComercio.repository.ProductoRepository;
import org.gestionComercio.security.SesionUsuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Usa el {@link RegistradorMovimientosInventario} real sobre repositorios
 * simulados, para probar también las reglas que viven en el núcleo.
 */
@ExtendWith(MockitoExtension.class)
class InventarioServiceImplTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Mock
    private MovimientoInventarioMapper movimientoInventarioMapper;

    private SesionUsuario sesionUsuario;

    private InventarioServiceImpl inventarioService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        usuario = Usuario.builder().id(5L).usuario("admin").nombre("Ana").apellido("Pérez").build();
        sesionUsuario.iniciarSesion(usuario, EnumSet.allOf(PermisoCodigo.class));

        RegistradorMovimientosInventario registrador = new RegistradorMovimientosInventario(
                productoRepository, movimientoInventarioRepository, sesionUsuario);

        inventarioService = new InventarioServiceImpl(
                registrador, movimientoInventarioRepository, movimientoInventarioMapper, sesionUsuario);

        lenient().when(movimientoInventarioRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(movimientoInventarioMapper.toDto(any()))
                .thenReturn(new MovimientoInventarioDto());
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private Producto producto(String stock) {
        return Producto.builder().id(1L).codigo("P001").nombre("Yerba").stock(bd(stock)).build();
    }

    private void existe(Producto producto) {
        when(productoRepository.findAllById(List.of(1L))).thenReturn(List.of(producto));
    }

    private MovimientoInventario movimientoGuardado() {
        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoInventarioRepository).save(captor.capture());
        return captor.getValue();
    }

    private void verificarSinCambios(Producto producto, String stockOriginal) {
        assertThat(producto.getStock()).isEqualByComparingTo(stockOriginal);
        verify(movimientoInventarioRepository, never()).save(any());
        verify(productoRepository, never()).saveAllAndFlush(anyList());
    }

    // ------------------------------------------------------------------
    // Entradas y salidas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("registrarEntrada() guarda un movimiento manual y suma la cantidad al stock")
    void registrarEntrada() {
        Producto producto = producto("10");
        existe(producto);

        inventarioService.registrarEntrada(1L, bd("5"), "Compra a proveedor");

        MovimientoInventario m = movimientoGuardado();
        assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.ENTRADA);
        assertThat(m.getCantidad()).isEqualByComparingTo("5");
        assertThat(m.getStockResultante()).isEqualByComparingTo("15");
        assertThat(m.getOrigen()).isEqualTo(OrigenMovimientoInventario.MANUAL);
        assertThat(m.getUsuario()).isSameAs(usuario);
        assertThat(producto.getStock()).isEqualByComparingTo("15");
        verify(productoRepository).saveAllAndFlush(List.of(producto));
    }

    @Test
    @DisplayName("registrarEntrada() y registrarSalida() rechazan cantidades nulas o no positivas")
    void rechazaCantidadNoPositiva() {
        assertThatThrownBy(() -> inventarioService.registrarEntrada(1L, BigDecimal.ZERO, "Compra"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> inventarioService.registrarSalida(1L, null, "Venta"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(movimientoInventarioRepository);
    }

    @Test
    @DisplayName("registrarSalida() registra la cantidad en negativo y la resta del stock")
    void registrarSalida() {
        Producto producto = producto("10");
        existe(producto);

        inventarioService.registrarSalida(1L, bd("4"), "Venta");

        MovimientoInventario m = movimientoGuardado();
        assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.SALIDA);
        assertThat(m.getCantidad()).isEqualByComparingTo("-4");
        assertThat(producto.getStock()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("registrarSalida() permite dejar el stock exactamente en cero")
    void registrarSalidaHastaCero() {
        Producto producto = producto("3");
        existe(producto);

        inventarioService.registrarSalida(1L, bd("3"), "Venta");

        assertThat(producto.getStock()).isZero();
    }

    @Test
    @DisplayName("registrarSalida() sin stock suficiente falla y no registra nada")
    void registrarSalidaSinStockSuficiente() {
        Producto producto = producto("2");
        existe(producto);

        assertThatThrownBy(() -> inventarioService.registrarSalida(1L, bd("3"), "Venta"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Stock insuficiente para \"Yerba\": disponible 2, solicitado 3.");

        verificarSinCambios(producto, "2");
    }

    @Test
    @DisplayName("registrarSalida() deja el stock negativo si el producto lo permite")
    void registrarSalidaConStockNegativoPermitido() {
        Producto producto = producto("2");
        producto.setPermiteStockNegativo(true);
        existe(producto);

        inventarioService.registrarSalida(1L, bd("5"), "Venta");

        assertThat(producto.getStock()).isEqualByComparingTo("-3");
    }

    @Test
    @DisplayName("Un producto por kilo admite decimales; uno por unidad no")
    void decimalesSegunLaUnidad() {
        Producto queso = producto("2");
        queso.setUnidadMedida(UnidadMedida.KILOGRAMO);
        existe(queso);

        inventarioService.registrarSalida(1L, bd("0.750"), "Venta");

        assertThat(queso.getStock()).isEqualByComparingTo("1.25");

        Producto yerba = producto("2");
        existe(yerba);

        assertThatThrownBy(() -> inventarioService.registrarSalida(1L, bd("0.5"), "Venta"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no admite cantidades con decimales");
        assertThat(yerba.getStock()).isEqualByComparingTo("2");
    }

    // ------------------------------------------------------------------
    // Ajustes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("registrarAjuste() guarda la causa y usa la observación como motivo")
    void registrarAjuste() {
        Producto producto = producto("10");
        existe(producto);

        inventarioService.registrarAjuste(1L, bd("-4"), MotivoAjusteInventario.ROTURA, "Se cayó una caja");

        MovimientoInventario m = movimientoGuardado();
        assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.AJUSTE);
        assertThat(m.getCantidad()).isEqualByComparingTo("-4");
        assertThat(m.getMotivoAjuste()).isEqualTo(MotivoAjusteInventario.ROTURA);
        assertThat(m.getMotivo()).isEqualTo("Se cayó una caja");
        assertThat(producto.getStock()).isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("registrarAjuste() sin observación usa la descripción de la causa como motivo")
    void registrarAjusteSinObservacion() {
        existe(producto("10"));

        inventarioService.registrarAjuste(1L, bd("-1"), MotivoAjusteInventario.VENCIMIENTO, "  ");

        assertThat(movimientoGuardado().getMotivo()).isEqualTo("Vencimiento");
    }

    @Test
    @DisplayName("registrarAjuste() exige una variación distinta de cero y una causa")
    void registrarAjusteValidaDatos() {
        assertThatThrownBy(() -> inventarioService.registrarAjuste(
                1L, BigDecimal.ZERO, MotivoAjusteInventario.OTRO, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cero");
        assertThatThrownBy(() -> inventarioService.registrarAjuste(1L, BigDecimal.ONE, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("causa");
    }

    @Test
    @DisplayName("registrarAjuste() no deja el stock negativo si el producto no lo permite")
    void registrarAjusteNoDejaStockNegativo() {
        Producto producto = producto("2");
        existe(producto);

        assertThatThrownBy(() -> inventarioService.registrarAjuste(
                1L, bd("-3"), MotivoAjusteInventario.RECUENTO, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no permite stock negativo");

        verificarSinCambios(producto, "2");
    }

    @Test
    @DisplayName("ajustarStock() registra la diferencia entre lo contado y el stock del sistema")
    void ajustarStockPorRecuento() {
        Producto producto = producto("10");
        existe(producto);

        inventarioService.ajustarStock(1L, bd("7"), MotivoAjusteInventario.RECUENTO, null);

        MovimientoInventario m = movimientoGuardado();
        assertThat(m.getCantidad()).isEqualByComparingTo("-3");
        assertThat(m.getMotivoAjuste()).isEqualTo(MotivoAjusteInventario.RECUENTO);
        assertThat(producto.getStock()).isEqualByComparingTo("7");
    }

    @Test
    @DisplayName("ajustarStock() puede llevar a cero un stock negativo")
    void ajustarStockDesdeNegativo() {
        Producto producto = producto("-2");
        existe(producto);

        inventarioService.ajustarStock(1L, BigDecimal.ZERO, MotivoAjusteInventario.RECUENTO, null);

        assertThat(movimientoGuardado().getCantidad()).isEqualByComparingTo("2");
        assertThat(producto.getStock()).isZero();
    }

    @Test
    @DisplayName("ajustarStock() falla si lo contado coincide con el stock o es negativo")
    void ajustarStockSinDiferencia() {
        Producto producto = producto("10");
        existe(producto);

        assertThatThrownBy(() -> inventarioService.ajustarStock(
                1L, bd("10.000"), MotivoAjusteInventario.RECUENTO, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("coincide");
        assertThatThrownBy(() -> inventarioService.ajustarStock(
                1L, bd("-1"), MotivoAjusteInventario.RECUENTO, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("negativo");

        verificarSinCambios(producto, "10");
    }

    @Test
    @DisplayName("ajustarStock() no se puede hacer sin motivo: exige la causa, y el detalle si es \"Otro\"")
    void ajustarStockSinMotivo() {
        Producto producto = producto("10");
        lenient().when(productoRepository.findAllById(List.of(1L))).thenReturn(List.of(producto));

        assertThatThrownBy(() -> inventarioService.ajustarStock(1L, bd("7"), null, "Contado"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("causa");
        assertThatThrownBy(() -> inventarioService.ajustarStock(
                1L, bd("7"), MotivoAjusteInventario.OTRO, "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("detallar");
        assertThatThrownBy(() -> inventarioService.registrarAjuste(
                1L, bd("-3"), MotivoAjusteInventario.OTRO, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("detallar");
        assertThatThrownBy(() -> inventarioService.ajustarStock(
                1L, bd("7"), MotivoAjusteInventario.ROTURA, "x".repeat(MovimientoInventario.MOTIVO_MAX + 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("superar");

        verificarSinCambios(producto, "10");
    }

    @Test
    @DisplayName("ajustarStock() con causa \"Otro\" y detalle usa el detalle como motivo")
    void ajustarStockOtroConDetalle() {
        Producto producto = producto("10");
        existe(producto);

        inventarioService.ajustarStock(1L, bd("12"), MotivoAjusteInventario.OTRO, "Devolución sin comprobante");

        MovimientoInventario m = movimientoGuardado();
        assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.AJUSTE);
        assertThat(m.getStockAnterior()).isEqualByComparingTo("10");
        assertThat(m.getCantidad()).isEqualByComparingTo("2");
        assertThat(m.getStockResultante()).isEqualByComparingTo("12");
        assertThat(m.getMotivo()).isEqualTo("Devolución sin comprobante");
        assertThat(m.getMotivoAjuste()).isEqualTo(MotivoAjusteInventario.OTRO);
    }

    // ------------------------------------------------------------------
    // Validaciones comunes
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Falla si el producto no existe o está dado de baja")
    void productoInexistenteODadoDeBaja() {
        when(productoRepository.findAllById(List.of(1L))).thenReturn(List.of());

        assertThatThrownBy(() -> inventarioService.registrarEntrada(1L, bd("5"), "Compra"))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("No existe un producto activo con ID: 1");

        Producto inactivo = producto("10");
        inactivo.setEstadoRegistro(EstadoRegistro.INACTIVO);
        existe(inactivo);

        assertThatThrownBy(() -> inventarioService.registrarEntrada(1L, bd("5"), "Compra"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Falla si el producto no maneja stock")
    void productoSinManejoDeStock() {
        Producto producto = producto("0");
        producto.setManejaStock(false);
        existe(producto);

        assertThatThrownBy(() -> inventarioService.registrarEntrada(1L, bd("5"), "Compra"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no maneja stock");

        verificarSinCambios(producto, "0");
    }

    @Test
    @DisplayName("Un motivo vacío se informa como error de negocio y no registra nada")
    void motivoObligatorio() {
        Producto producto = producto("10");
        existe(producto);

        assertThatThrownBy(() -> inventarioService.registrarEntrada(1L, bd("5"), "   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("motivo");

        verificarSinCambios(producto, "10");
    }

    // ------------------------------------------------------------------
    // Transaccionalidad y concurrencia
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Guarda el movimiento antes de actualizar el stock del producto")
    void guardaMovimientoAntesQueElStock() {
        Producto producto = producto("10");
        existe(producto);

        inventarioService.registrarEntrada(1L, bd("5"), "Compra");

        InOrder orden = inOrder(movimientoInventarioRepository, productoRepository);
        orden.verify(movimientoInventarioRepository).save(any());
        orden.verify(productoRepository).saveAllAndFlush(List.of(producto));
    }

    @Test
    @DisplayName("Si falla el registro del movimiento, el stock no se actualiza")
    void siFallaElMovimientoNoActualizaElStock() {
        Producto producto = producto("10");
        existe(producto);
        when(movimientoInventarioRepository.save(any())).thenThrow(new RuntimeException("Error de base"));

        assertThatThrownBy(() -> inventarioService.registrarEntrada(1L, bd("5"), "Compra"))
                .isInstanceOf(RuntimeException.class);

        assertThat(producto.getStock()).isEqualByComparingTo("10");
        verify(productoRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("Un conflicto de @Version se informa como ConcurrencyException")
    void conflictoDeVersion() {
        Producto producto = producto("10");
        existe(producto);
        when(productoRepository.saveAllAndFlush(anyList()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Producto.class, 1L));

        assertThatThrownBy(() -> inventarioService.registrarSalida(1L, bd("3"), "Venta"))
                .isInstanceOf(ConcurrencyException.class)
                .hasMessageContaining("\"Yerba\" fue modificado por otra operación");
    }

    @Test
    @DisplayName("La transacción se revierte ante cualquier excepción")
    void transaccionalAnteCualquierExcepcion() {
        Transactional transactional = InventarioServiceImpl.class.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Test
    @DisplayName("historial() devuelve los movimientos del producto")
    void historial() {
        when(movimientoInventarioRepository.findHistorialPorProducto(1L)).thenReturn(List.of());
        when(movimientoInventarioMapper.toDtoList(List.of())).thenReturn(List.of());

        assertThat(inventarioService.historial(1L)).isEmpty();
    }

    @Test
    @DisplayName("buscarMovimientos() incluye el día completo de ambas fechas")
    void buscarMovimientosIncluyeLosDiasCompletos() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 17);

        inventarioService.buscarMovimientos(desde, hasta, null);

        verify(movimientoInventarioRepository).consultar(
                null, null, LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(hasta, LocalTime.MAX));
    }

    @Test
    @DisplayName("consultarMovimientos() pasa todos los filtros al repositorio, con los días completos")
    void consultarMovimientosConFiltros() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 17);

        inventarioService.consultarMovimientos(
                new FiltroMovimientos(7L, TipoMovimientoInventario.SALIDA, desde, hasta));

        verify(movimientoInventarioRepository).consultar(
                7L, TipoMovimientoInventario.SALIDA,
                LocalDateTime.of(2026, 11, 1, 0, 0), LocalDateTime.of(hasta, LocalTime.MAX));
    }

    @Test
    @DisplayName("consultarMovimientos() sin filtros trae todos los movimientos")
    void consultarMovimientosSinFiltros() {
        inventarioService.consultarMovimientos(FiltroMovimientos.todos());
        inventarioService.consultarMovimientos(null);

        verify(movimientoInventarioRepository, times(2)).consultar(null, null, null, null);
    }

    @Test
    @DisplayName("consultarMovimientos() admite una sola de las fechas")
    void consultarMovimientosConUnaSolaFecha() {
        LocalDate dia = LocalDate.of(2026, 11, 10);

        inventarioService.consultarMovimientos(new FiltroMovimientos(null, null, dia, null));
        inventarioService.consultarMovimientos(new FiltroMovimientos(null, null, null, dia));

        verify(movimientoInventarioRepository).consultar(null, null, dia.atStartOfDay(), null);
        verify(movimientoInventarioRepository).consultar(null, null, null, dia.atTime(LocalTime.MAX));
    }

    @Test
    @DisplayName("consultarMovimientos() rechaza un rango invertido y exige INVENTARIO_VER")
    void consultarMovimientosValidaYExigePermiso() {
        LocalDate hoy = LocalDate.of(2026, 11, 17);

        assertThatThrownBy(() -> inventarioService.consultarMovimientos(
                new FiltroMovimientos(null, null, hoy, hoy.minusDays(1))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("posterior");

        sesionUsuario.iniciarSesion(usuario, EnumSet.of(PermisoCodigo.INVENTARIO_AJUSTAR));

        assertThatThrownBy(() -> inventarioService.consultarMovimientos(FiltroMovimientos.todos()))
                .isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(movimientoInventarioRepository);
    }

    @Test
    @DisplayName("buscarMovimientos() valida el rango de fechas")
    void buscarMovimientosValidaFechas() {
        LocalDate hoy = LocalDate.of(2026, 11, 17);

        assertThatThrownBy(() -> inventarioService.buscarMovimientos(hoy, hoy.minusDays(1), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("posterior");
        assertThatThrownBy(() -> inventarioService.buscarMovimientos(null, hoy, null))
                .isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------------
    // Permisos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Cada operación exige su propio permiso")
    void cadaOperacionExigeSuPermiso() {
        sesionUsuario.iniciarSesion(usuario, EnumSet.of(PermisoCodigo.INVENTARIO_REGISTRAR_ENTRADA));
        existe(producto("10"));

        inventarioService.registrarEntrada(1L, bd("5"), "Compra");

        assertThatThrownBy(() -> inventarioService.registrarSalida(1L, BigDecimal.ONE, "Venta"))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> inventarioService.registrarAjuste(
                1L, BigDecimal.ONE, MotivoAjusteInventario.OTRO, null))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> inventarioService.ajustarStock(
                1L, BigDecimal.ONE, MotivoAjusteInventario.OTRO, null))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(() -> inventarioService.historial(1L))
                .isInstanceOf(AuthorizationException.class);

        verify(movimientoInventarioRepository).save(any());
    }

    @Test
    @DisplayName("Sin sesión o sin permiso no consulta el producto ni registra nada")
    void sinPermisoNoHaceNada() {
        sesionUsuario.iniciarSesion(usuario, EnumSet.noneOf(PermisoCodigo.class));

        assertThatThrownBy(() -> inventarioService.registrarAjuste(
                1L, BigDecimal.ONE, MotivoAjusteInventario.OTRO, null))
                .isInstanceOf(AuthorizationException.class);

        sesionUsuario.cerrarSesion();

        assertThatThrownBy(() -> inventarioService.registrarEntrada(1L, BigDecimal.ONE, "Compra"))
                .isInstanceOf(AuthorizationException.class);

        verifyNoInteractions(productoRepository, movimientoInventarioRepository);
    }

}
