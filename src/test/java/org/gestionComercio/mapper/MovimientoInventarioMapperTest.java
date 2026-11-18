package org.gestionComercio.mapper;

import org.gestionComercio.dto.inventario.MovimientoInventarioDto;
import org.gestionComercio.entity.MovimientoInventario;
import org.gestionComercio.entity.Producto;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.TipoMovimientoInventario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MovimientoInventarioMapperTest {

    private final MovimientoInventarioMapper mapper = new MovimientoInventarioMapperImpl();

    @Test
    void toDtoCopiaLosDatosDelMovimientoDelProductoYDelResponsable() {

        Producto producto = Producto.builder().id(1L).codigo("P001").nombre("Yerba").stock(10).build();
        Usuario usuario = Usuario.builder().id(5L).usuario("aperez").nombre("Ana").apellido("Pérez").build();

        MovimientoInventario movimiento = MovimientoInventario.registrar(
                producto, TipoMovimientoInventario.SALIDA, -3, "Venta", usuario);
        movimiento.setId(99L);

        MovimientoInventarioDto dto = mapper.toDto(movimiento);

        assertThat(dto.getId()).isEqualTo(99L);
        assertThat(dto.getProductoId()).isEqualTo(1L);
        assertThat(dto.getProductoCodigo()).isEqualTo("P001");
        assertThat(dto.getProductoNombre()).isEqualTo("Yerba");
        assertThat(dto.getTipo()).isEqualTo(TipoMovimientoInventario.SALIDA);
        assertThat(dto.getCantidad()).isEqualTo(-3);
        assertThat(dto.getStockAnterior()).isEqualTo(10);
        assertThat(dto.getStockResultante()).isEqualTo(7);
        assertThat(dto.getFecha()).isEqualTo(movimiento.getFecha());
        assertThat(dto.getMotivo()).isEqualTo("Venta");
        assertThat(dto.getUsuarioId()).isEqualTo(5L);
        assertThat(dto.getUsuarioNombre()).isEqualTo("aperez");
        assertThat(dto.getUsuarioNombreCompleto()).isEqualTo("Ana Pérez");
    }

    @Test
    void toDtoListConvierteCadaMovimiento() {

        Producto producto = Producto.builder().id(1L).codigo("P001").nombre("Yerba").stock(0).build();
        Usuario usuario = Usuario.builder().id(5L).usuario("aperez").nombre("Ana").apellido("Pérez").build();

        List<MovimientoInventarioDto> dtos = mapper.toDtoList(List.of(
                MovimientoInventario.registrar(producto, TipoMovimientoInventario.ENTRADA, 2, "Compra", usuario),
                MovimientoInventario.registrar(producto, TipoMovimientoInventario.AJUSTE, -1, "Rotura", usuario)));

        assertThat(dtos).extracting(MovimientoInventarioDto::getCantidad).containsExactly(2, -1);
    }

    @Test
    void toDtoDeNullDevuelveNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
