package org.gestionComercio.service.impl;

import org.gestionComercio.dto.inventario.LineaInventario;
import org.gestionComercio.entity.MovimientoInventario;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventarioComprobanteServiceImplTest {

    private static final OrigenMovimientoInventario VENTA = OrigenMovimientoInventario.VENTA;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private MovimientoInventarioRepository movimientoInventarioRepository;

    @Mock
    private MovimientoInventarioMapper movimientoInventarioMapper;

    private SesionUsuario sesionUsuario;

    private InventarioComprobanteServiceImpl service;

    private Usuario usuario;

    private Producto yerba;
    private Producto azucar;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        usuario = Usuario.builder().id(5L).usuario("vendedor").nombre("Ana").apellido("Pérez").build();
        // Sin permisos de inventario: el comprobante ya controló los suyos.
        sesionUsuario.iniciarSesion(usuario, EnumSet.noneOf(org.gestionComercio.enums.PermisoCodigo.class));

        service = new InventarioComprobanteServiceImpl(
                new RegistradorMovimientosInventario(productoRepository, movimientoInventarioRepository, sesionUsuario),
                movimientoInventarioRepository, movimientoInventarioMapper);

        yerba = Producto.builder().id(1L).codigo("P001").nombre("Yerba").stock(new BigDecimal("10")).build();
        azucar = Producto.builder().id(2L).codigo("P002").nombre("Azúcar").stock(new BigDecimal("1")).build();

        lenient().when(movimientoInventarioRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static LineaInventario linea(long productoId, String cantidad) {
        return new LineaInventario(productoId, new BigDecimal(cantidad));
    }

    private List<MovimientoInventario> movimientosGuardados(int cantidad) {
        ArgumentCaptor<MovimientoInventario> captor = ArgumentCaptor.forClass(MovimientoInventario.class);
        verify(movimientoInventarioRepository, times(cantidad)).save(captor.capture());
        return captor.getAllValues();
    }

    // ------------------------------------------------------------------
    // Registro
    // ------------------------------------------------------------------

    @Test
    @DisplayName("registrarSalidas() descuenta cada producto con el origen y el comprobante")
    void registrarSalidas() {
        when(productoRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(yerba, azucar));

        service.registrarSalidas(VENTA, 40L, List.of(linea(1, "3"), linea(2, "1")), "Venta N° 40");

        List<MovimientoInventario> guardados = movimientosGuardados(2);
        assertThat(guardados).allSatisfy(m -> {
            assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.SALIDA);
            assertThat(m.getOrigen()).isEqualTo(VENTA);
            assertThat(m.getOrigenId()).isEqualTo(40L);
            assertThat(m.getUsuario()).isSameAs(usuario);
        });
        assertThat(yerba.getStock()).isEqualByComparingTo("7");
        assertThat(azucar.getStock()).isZero();
        verify(productoRepository).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("Los renglones del mismo producto se suman en un solo movimiento")
    void agrupaRenglonesDelMismoProducto() {
        when(productoRepository.findAllById(Set.of(1L))).thenReturn(List.of(yerba));

        service.registrarSalidas(VENTA, 40L, List.of(linea(1, "2"), linea(1, "3")), "Venta");

        assertThat(movimientosGuardados(1).get(0).getCantidad()).isEqualByComparingTo("-5");
        assertThat(yerba.getStock()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("Valida todos los renglones e informa juntos todos los productos sin stock")
    void informaTodosLosFaltantesJuntos() {
        Producto cafe = Producto.builder().id(3L).nombre("Café").stock(BigDecimal.ZERO).build();
        when(productoRepository.findAllById(Set.of(1L, 2L, 3L))).thenReturn(List.of(yerba, azucar, cafe));

        assertThatThrownBy(() -> service.registrarSalidas(VENTA, 40L,
                List.of(linea(1, "1"), linea(2, "5"), linea(3, "2")), "Venta"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("\"Azúcar\": disponible 1, solicitado 5")
                .hasMessageContaining("\"Café\": disponible 0, solicitado 2")
                .hasMessageNotContaining("Yerba");

        verify(movimientoInventarioRepository, never()).save(any());
        assertThat(yerba.getStock()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Los productos que no manejan stock se ignoran")
    void ignoraProductosSinStock() {
        azucar.setManejaStock(false);
        when(productoRepository.findAllById(Set.of(2L))).thenReturn(List.of(azucar));

        assertThat(service.registrarSalidas(VENTA, 40L, List.of(linea(2, "5")), "Venta")).isEmpty();

        verify(movimientoInventarioRepository, never()).save(any());
        verify(productoRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("registrarEntradas() suma al stock")
    void registrarEntradas() {
        when(productoRepository.findAllById(Set.of(1L))).thenReturn(List.of(yerba));

        service.registrarEntradas(OrigenMovimientoInventario.COMPRA, 8L, List.of(linea(1, "12")), "Compra N° 8");

        MovimientoInventario m = movimientosGuardados(1).get(0);
        assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.ENTRADA);
        assertThat(m.getOrigen()).isEqualTo(OrigenMovimientoInventario.COMPRA);
        assertThat(yerba.getStock()).isEqualByComparingTo("22");
    }

    @Test
    @DisplayName("Un comprobante no puede registrar sus movimientos dos veces")
    void noRegistraDosVecesElMismoComprobante() {
        when(movimientoInventarioRepository.existsByOrigenAndOrigenIdAndMovimientoRevertidoIsNull(VENTA, 40L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.registrarSalidas(VENTA, 40L, List.of(linea(1, "1")), "Venta"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("La venta N° 40 ya registró sus movimientos de stock.");

        verify(movimientoInventarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rechaza renglones inválidos, productos dados de baja y orígenes manuales")
    void validaDatos() {
        assertThatThrownBy(() -> service.registrarSalidas(VENTA, 40L, List.of(), "Venta"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no tiene renglones");
        assertThatThrownBy(() -> service.registrarSalidas(VENTA, 40L, List.of(linea(1, "0")), "Venta"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("mayor que cero");
        assertThatThrownBy(() -> service.registrarSalidas(OrigenMovimientoInventario.MANUAL, 40L,
                List.of(linea(1, "1")), "Venta"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.registrarSalidas(VENTA, null, List.of(linea(1, "1")), "Venta"))
                .isInstanceOf(IllegalArgumentException.class);

        yerba.setEstadoRegistro(EstadoRegistro.INACTIVO);
        when(productoRepository.findAllById(Set.of(1L))).thenReturn(List.of(yerba));

        assertThatThrownBy(() -> service.registrarSalidas(VENTA, 40L, List.of(linea(1, "1")), "Venta"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Sólo exige sesión iniciada, no permisos de inventario")
    void exigeSesion() {
        sesionUsuario.cerrarSesion();

        assertThatThrownBy(() -> service.registrarSalidas(VENTA, 40L, List.of(linea(1, "1")), "Venta"))
                .isInstanceOf(AuthorizationException.class);
    }

    // ------------------------------------------------------------------
    // Reversión
    // ------------------------------------------------------------------

    private MovimientoInventario salidaDeVenta(Producto producto, String cantidad) {
        MovimientoInventario m = MovimientoInventario.nuevo().producto(producto)
                .tipo(TipoMovimientoInventario.SALIDA).cantidad(new BigDecimal(cantidad).negate())
                .motivo("Venta").origen(VENTA).origenId(40L).usuario(usuario).registrar();
        producto.aplicarMovimiento(m);
        return m;
    }

    @Test
    @DisplayName("revertir() registra el movimiento opuesto de cada original y restituye el stock")
    void revertir() {
        MovimientoInventario m1 = salidaDeVenta(yerba, "3");
        MovimientoInventario m2 = salidaDeVenta(azucar, "1");
        when(movimientoInventarioRepository.findOriginalesDelComprobante(VENTA, 40L)).thenReturn(List.of(m1, m2));

        service.revertir(VENTA, 40L, "Anulación de la venta N° 40");

        List<MovimientoInventario> reversiones = movimientosGuardados(2);
        assertThat(reversiones).extracting(MovimientoInventario::getMovimientoRevertido).containsExactly(m1, m2);
        assertThat(reversiones).allSatisfy(m -> assertThat(m.getTipo()).isEqualTo(TipoMovimientoInventario.ENTRADA));
        assertThat(yerba.getStock()).isEqualByComparingTo("10");
        assertThat(azucar.getStock()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("revertir() funciona aunque el producto se haya dado de baja")
    void revertirProductoDadoDeBaja() {
        MovimientoInventario m = salidaDeVenta(yerba, "3");
        yerba.setEstadoRegistro(EstadoRegistro.INACTIVO);
        when(movimientoInventarioRepository.findOriginalesDelComprobante(VENTA, 40L)).thenReturn(List.of(m));

        service.revertir(VENTA, 40L, "Anulación");

        assertThat(yerba.getStock()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("revertir() falla si el comprobante ya fue revertido")
    void revertirDosVeces() {
        when(movimientoInventarioRepository.existsByOrigenAndOrigenIdAndMovimientoRevertidoIsNotNull(VENTA, 40L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.revertir(VENTA, 40L, "Anulación"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Los movimientos de stock de la venta N° 40 ya fueron revertidos.");
    }

    @Test
    @DisplayName("revertir() de un comprobante sin movimientos no hace nada")
    void revertirSinMovimientos() {
        when(movimientoInventarioRepository.findOriginalesDelComprobante(VENTA, 40L)).thenReturn(List.of());

        assertThat(service.revertir(VENTA, 40L, "Anulación")).isEmpty();
        verify(productoRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("Revertir una compra cuya mercadería ya se vendió no deja stock negativo")
    void revertirCompraSinStock() {
        MovimientoInventario compra = MovimientoInventario.nuevo().producto(azucar)
                .tipo(TipoMovimientoInventario.ENTRADA).cantidad(new BigDecimal("5")).motivo("Compra")
                .origen(OrigenMovimientoInventario.COMPRA).origenId(8L).usuario(usuario).registrar();
        when(movimientoInventarioRepository.findOriginalesDelComprobante(OrigenMovimientoInventario.COMPRA, 8L))
                .thenReturn(List.of(compra));

        // El azúcar tiene stock 1: no se pueden sacar las 5 unidades de la compra.
        assertThatThrownBy(() -> service.revertir(OrigenMovimientoInventario.COMPRA, 8L, "Anulación"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuficiente para \"Azúcar\"");
    }

}
