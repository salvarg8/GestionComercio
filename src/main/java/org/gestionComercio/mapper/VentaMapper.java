package org.gestionComercio.mapper;

import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.entity.Venta;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Sólo convierte de entidad a DTO: las ventas las crea {@code VentaService}.
 *
 * <p>{@link #toDto} incluye los renglones; {@link #toResumen} no los toca,
 * para listar ventas sin cargar sus detalles.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = DetalleVentaMapper.class
)
public interface VentaMapper {

    @Mapping(target = "clienteId", source = "cliente.id")
    @Mapping(target = "clienteNombre", expression = "java(nombreCliente(entity.getCliente()))")
    @Mapping(target = "cajaId", source = "caja.id")
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioNombre", source = "usuario.usuario")
    @Mapping(target = "usuarioNombreCompleto", expression = "java(nombreCompleto(entity.getUsuario()))")
    @Mapping(target = "usuarioAnulacionNombreCompleto",
            expression = "java(nombreCompleto(entity.getUsuarioAnulacion()))")
    VentaDto toDto(Venta entity);

    @Named("resumen")
    @Mapping(target = "detalles", ignore = true)
    @Mapping(target = "clienteId", source = "cliente.id")
    @Mapping(target = "clienteNombre", expression = "java(nombreCliente(entity.getCliente()))")
    @Mapping(target = "cajaId", source = "caja.id")
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioNombre", source = "usuario.usuario")
    @Mapping(target = "usuarioNombreCompleto", expression = "java(nombreCompleto(entity.getUsuario()))")
    @Mapping(target = "usuarioAnulacionNombreCompleto",
            expression = "java(nombreCompleto(entity.getUsuarioAnulacion()))")
    VentaDto toResumen(Venta entity);

    @IterableMapping(qualifiedByName = "resumen")
    List<VentaDto> toResumenList(List<Venta> entities);

    /** "Apellido, Nombre", sólo el nombre (razón social) si no tiene apellido, o consumidor final. */
    default String nombreCliente(Cliente cliente) {

        if (cliente == null) {
            return VentaDto.CONSUMIDOR_FINAL;
        }

        return cliente.getApellido() == null || cliente.getApellido().isBlank()
                ? cliente.getNombre()
                : cliente.getApellido() + ", " + cliente.getNombre();
    }

    /** "Nombre Apellido" del usuario, o null si no hay usuario. */
    default String nombreCompleto(Usuario usuario) {
        return usuario == null ? null : usuario.getNombre() + " " + usuario.getApellido();
    }

}
