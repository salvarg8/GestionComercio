package org.gestionComercio.controller.inventario;

import org.gestionComercio.controller.inventario.MovimientoStockDialog.Operacion;
import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.dto.inventario.StockProductoDto;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.service.InventarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Lógica del formulario de entrada/salida de stock, sin abrir la ventana
 * (no requiere el toolkit de JavaFX).
 */
class MovimientoStockDialogTest {

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor);
    }

    private static StockProductoDto producto(UnidadMedida unidad, String stock, boolean permiteNegativo) {
        return new StockProductoDto(1L, "YER001", null, "Yerba Mate 1kg", 1L, "Almacén", "Marca",
                unidad, bd(stock), bd("2"), true, permiteNegativo, EstadoRegistro.ACTIVO);
    }

    private static StockProductoDto enUnidades(String stock) {
        return producto(UnidadMedida.UNIDAD, stock, false);
    }

    // ------------------------------------------------------------------
    // Stock resultante
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La entrada suma al stock actual y la salida resta")
    void stockResultante() {

        StockProductoDto p = enUnidades("10");

        assertThat(MovimientoStockDialog.calcularStockResultante(p, Operacion.ENTRADA, bd("5")))
                .isEqualByComparingTo("15");
        assertThat(MovimientoStockDialog.calcularStockResultante(p, Operacion.SALIDA, bd("4")))
                .isEqualByComparingTo("6");
    }

    @Test
    @DisplayName("El stock resultante respeta los decimales de los productos fraccionables")
    void stockResultanteFraccionable() {

        StockProductoDto p = producto(UnidadMedida.KILOGRAMO, "2.5", false);

        assertThat(MovimientoStockDialog.calcularStockResultante(p, Operacion.SALIDA, bd("0.750")))
                .isEqualByComparingTo("1.75");
    }

    @Test
    @DisplayName("Un producto sin stock cargado (null) cuenta como stock cero")
    void stockNuloEsCero() {

        StockProductoDto p = new StockProductoDto(1L, "P1", null, "Producto", 1L, "Almacén", "Marca",
                UnidadMedida.UNIDAD, null, null, true, false, EstadoRegistro.ACTIVO);

        assertThat(MovimientoStockDialog.calcularStockResultante(p, Operacion.ENTRADA, bd("3")))
                .isEqualByComparingTo("3");
    }

    // ------------------------------------------------------------------
    // Validación de la cantidad
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una cantidad positiva y entera es válida")
    void cantidadValida() {
        assertThat(MovimientoStockDialog.validarCantidad(enUnidades("10"), bd("3"))).isNull();
    }

    @Test
    @DisplayName("La cantidad es obligatoria y debe ser mayor que cero")
    void cantidadObligatoriaYPositiva() {

        StockProductoDto p = enUnidades("10");

        assertThat(MovimientoStockDialog.validarCantidad(p, null)).contains("Debe indicar la cantidad");
        assertThat(MovimientoStockDialog.validarCantidad(p, BigDecimal.ZERO)).contains("mayor que cero");
        assertThat(MovimientoStockDialog.validarCantidad(p, bd("-1"))).contains("mayor que cero");
    }

    @Test
    @DisplayName("Los productos que se miden en unidades enteras no admiten decimales")
    void unidadesEnteras() {

        assertThat(MovimientoStockDialog.validarCantidad(enUnidades("10"), bd("1.5")))
                .contains("unidades enteras");

        // 2,000 es entero aunque tenga decimales escritos.
        assertThat(MovimientoStockDialog.validarCantidad(enUnidades("10"), bd("2.000"))).isNull();
    }

    @Test
    @DisplayName("Los productos fraccionables admiten hasta 3 decimales")
    void fraccionables() {

        StockProductoDto p = producto(UnidadMedida.LITRO, "10", false);

        assertThat(MovimientoStockDialog.validarCantidad(p, bd("1.255"))).isNull();
        assertThat(MovimientoStockDialog.validarCantidad(p, bd("1.2555"))).contains("3 decimales");
    }

    @Test
    @DisplayName("La cantidad no puede superar el máximo admitido")
    void cantidadMaxima() {
        assertThat(MovimientoStockDialog.validarCantidad(enUnidades("0"), bd("1000000000000")))
                .contains("máximo");
    }

    // ------------------------------------------------------------------
    // Validación del stock resultante
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una salida que deja el stock en cero es válida")
    void salidaHastaCero() {
        assertThat(MovimientoStockDialog.validarStockResultante(enUnidades("5"), Operacion.SALIDA, bd("5")))
                .isNull();
    }

    @Test
    @DisplayName("Una salida mayor que el stock informa lo disponible y lo solicitado")
    void salidaSinStockSuficiente() {

        assertThat(MovimientoStockDialog.validarStockResultante(enUnidades("5"), Operacion.SALIDA, bd("8")))
                .isEqualTo("Stock insuficiente: disponible 5 (Unidad), solicitado 8.");
    }

    @Test
    @DisplayName("Si el producto permite stock negativo, la salida puede superar el stock")
    void salidaConStockNegativoPermitido() {

        StockProductoDto p = producto(UnidadMedida.UNIDAD, "5", true);

        assertThat(MovimientoStockDialog.validarStockResultante(p, Operacion.SALIDA, bd("8"))).isNull();
    }

    @Test
    @DisplayName("Si el stock ya es negativo, una entrada que no lo cubre sólo vale si el producto lo permite")
    void entradaSobreStockNegativo() {

        // El stock pudo quedar negativo cuando el producto lo permitía.
        assertThat(MovimientoStockDialog.validarStockResultante(enUnidades("-10"), Operacion.ENTRADA, bd("3")))
                .isEqualTo("La entrada dejaría el stock en -7 y el producto no permite stock negativo.");

        assertThat(MovimientoStockDialog.validarStockResultante(enUnidades("-10"), Operacion.ENTRADA, bd("10")))
                .isNull();

        assertThat(MovimientoStockDialog.validarStockResultante(
                producto(UnidadMedida.UNIDAD, "-10", true), Operacion.ENTRADA, bd("3")))
                .isNull();
    }

    @Test
    @DisplayName("Una entrada no puede dejar el stock por encima del máximo admitido")
    void entradaSuperaMaximo() {

        assertThat(MovimientoStockDialog.validarStockResultante(
                enUnidades("999999999999"), Operacion.ENTRADA, bd("1")))
                .contains("stock resultante supera el máximo");
    }

    // ------------------------------------------------------------------
    // Registro
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Una entrada se registra con registrarEntrada y el motivo sin espacios sobrantes")
    void ejecutarEntrada() {

        InventarioService servicio = mock(InventarioService.class);
        MovimientoInventarioDto esperado = new MovimientoInventarioDto();
        when(servicio.registrarEntrada(any(), any(), any())).thenReturn(esperado);

        MovimientoInventarioDto resultado = MovimientoStockDialog.ejecutar(
                servicio, enUnidades("10"), Operacion.ENTRADA, bd("5"), "  Mercadería recibida  ");

        assertThat(resultado).isSameAs(esperado);
        verify(servicio).registrarEntrada(1L, bd("5"), "Mercadería recibida");
        verifyNoMoreInteractions(servicio);
    }

    @Test
    @DisplayName("Una salida se registra con registrarSalida, con la cantidad en positivo")
    void ejecutarSalida() {

        InventarioService servicio = mock(InventarioService.class);
        MovimientoInventarioDto esperado = new MovimientoInventarioDto();
        when(servicio.registrarSalida(any(), any(), any())).thenReturn(esperado);

        MovimientoInventarioDto resultado = MovimientoStockDialog.ejecutar(
                servicio, enUnidades("10"), Operacion.SALIDA, bd("4"), "Consumo interno");

        assertThat(resultado).isSameAs(esperado);
        verify(servicio).registrarSalida(1L, bd("4"), "Consumo interno");
        verifyNoMoreInteractions(servicio);
    }

    @Test
    @DisplayName("Los errores del servicio (stock, permisos, concurrencia) llegan al formulario")
    void ejecutarPropagaErroresDelServicio() {

        InventarioService servicio = mock(InventarioService.class);
        when(servicio.registrarSalida(any(), any(), any()))
                .thenThrow(new BusinessException("Stock insuficiente para \"Yerba Mate 1kg\""));

        assertThatThrownBy(() -> MovimientoStockDialog.ejecutar(
                servicio, enUnidades("10"), Operacion.SALIDA, bd("4"), "Consumo interno"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Stock insuficiente");
    }
}
