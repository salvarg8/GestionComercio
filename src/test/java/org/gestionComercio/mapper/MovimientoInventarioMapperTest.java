package org.gestionComercio.mapper;

import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.entity.MovimientoInventario;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.MotivoAjusteInventario;
import org.gestionComercio.enums.OrigenMovimientoInventario;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.gestionComercio.enums.UnidadMedida;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MovimientoInventarioMapperTest {

    private final MovimientoInventarioMapper mapper = new MovimientoInventarioMapperImpl();

    private final Usuario usuario =
            Usuario.builder().id(5L).usuario("aperez").nombre("Ana").apellido("Pérez").build();

    @Test
    void toDtoCopiaLosDatosDelMovimientoDelProductoYDelResponsable() {

        Producto producto = Producto.builder().id(1L).codigo("P001").nombre("Queso")
                .unidadMedida(UnidadMedida.KILOGRAMO).stock(new BigDecimal("10")).build();

        MovimientoInventario movimiento = MovimientoInventario.nuevo()
                .producto(producto)
                .tipo(TipoMovimientoInventario.SALIDA)
                .cantidad(new BigDecimal("-0.75"))
                .motivo("Venta")
                .origen(OrigenMovimientoInventario.VENTA)
                .origenId(40L)
                .usuario(usuario)
                .registrar();
        movimiento.setId(99L);

        MovimientoInventarioDto dto = mapper.toDto(movimiento);

        assertThat(dto.getId()).isEqualTo(99L);
        assertThat(dto.getProductoId()).isEqualTo(1L);
        assertThat(dto.getProductoCodigo()).isEqualTo("P001");
        assertThat(dto.getProductoNombre()).isEqualTo("Queso");
        assertThat(dto.getProductoUnidadMedida()).isEqualTo(UnidadMedida.KILOGRAMO);
        assertThat(dto.getTipo()).isEqualTo(TipoMovimientoInventario.SALIDA);
        assertThat(dto.getCantidad()).isEqualByComparingTo("-0.75");
        assertThat(dto.getStockAnterior()).isEqualByComparingTo("10");
        assertThat(dto.getStockResultante()).isEqualByComparingTo("9.25");
        assertThat(dto.getFecha()).isEqualTo(movimiento.getFecha());
        assertThat(dto.getMotivo()).isEqualTo("Venta");
        assertThat(dto.getOrigen()).isEqualTo(OrigenMovimientoInventario.VENTA);
        assertThat(dto.getOrigenId()).isEqualTo(40L);
        assertThat(dto.getMovimientoRevertidoId()).isNull();
        assertThat(dto.getUsuarioId()).isEqualTo(5L);
        assertThat(dto.getUsuarioNombre()).isEqualTo("aperez");
        assertThat(dto.getUsuarioNombreCompleto()).isEqualTo("Ana Pérez");
    }

    @Test
    void toDtoDeUnaReversionIndicaElMovimientoRevertido() {

        Producto producto = Producto.builder().id(1L).nombre("Yerba").stock(BigDecimal.TEN).build();

        MovimientoInventario original = MovimientoInventario.nuevo()
                .producto(producto).tipo(TipoMovimientoInventario.SALIDA).cantidad(BigDecimal.ONE.negate())
                .motivo("Venta").origen(OrigenMovimientoInventario.VENTA).origenId(40L).usuario(usuario)
                .registrar();
        original.setId(7L);

        MovimientoInventarioDto dto = mapper.toDto(MovimientoInventario.revertir(original, "Anulación", usuario));

        assertThat(dto.getMovimientoRevertidoId()).isEqualTo(7L);
    }

    @Test
    void toDtoListConvierteCadaMovimiento() {

        Producto producto = Producto.builder().id(1L).codigo("P001").nombre("Yerba").build();

        List<MovimientoInventarioDto> dtos = mapper.toDtoList(List.of(
                MovimientoInventario.nuevo().producto(producto).tipo(TipoMovimientoInventario.ENTRADA)
                        .cantidad(new BigDecimal("2")).motivo("Compra").usuario(usuario).registrar(),
                MovimientoInventario.nuevo().producto(producto).tipo(TipoMovimientoInventario.AJUSTE)
                        .cantidad(new BigDecimal("-1")).motivo("Rotura")
                        .motivoAjuste(MotivoAjusteInventario.ROTURA).usuario(usuario).registrar()));

        assertThat(dtos).extracting(d -> d.getCantidad().intValueExact()).containsExactly(2, -1);
        assertThat(dtos.get(1).getMotivoAjuste()).isEqualTo(MotivoAjusteInventario.ROTURA);
    }

    @Test
    void toDtoDeNullDevuelveNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
