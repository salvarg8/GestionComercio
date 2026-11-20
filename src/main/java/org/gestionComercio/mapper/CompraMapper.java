package org.gestionComercio.mapper;

import org.gestionComercio.dto.compra.CompraDto;
import org.gestionComercio.entity.Compra;
import org.gestionComercio.entity.Usuario;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Sólo convierte de entidad a DTO: las compras las crea {@code CompraService}.
 *
 * <p>{@link #toDto} incluye los renglones; {@link #toResumen} no los toca,
 * para listar compras sin cargar sus detalles.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = DetalleCompraMapper.class
)
public interface CompraMapper {

    @Mapping(target = "actualizarPreciosCompra", ignore = true)
    @Mapping(target = "proveedorId", source = "proveedor.id")
    @Mapping(target = "proveedorNombre", source = "proveedor.razonSocial")
    @Mapping(target = "cajaId", source = "caja.id")
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioNombre", source = "usuario.usuario")
    @Mapping(target = "usuarioNombreCompleto", expression = "java(nombreCompleto(entity.getUsuario()))")
    @Mapping(target = "usuarioAnulacionNombreCompleto",
            expression = "java(nombreCompleto(entity.getUsuarioAnulacion()))")
    CompraDto toDto(Compra entity);

    @Named("resumen")
    @Mapping(target = "detalles", ignore = true)
    @Mapping(target = "actualizarPreciosCompra", ignore = true)
    @Mapping(target = "proveedorId", source = "proveedor.id")
    @Mapping(target = "proveedorNombre", source = "proveedor.razonSocial")
    @Mapping(target = "cajaId", source = "caja.id")
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioNombre", source = "usuario.usuario")
    @Mapping(target = "usuarioNombreCompleto", expression = "java(nombreCompleto(entity.getUsuario()))")
    @Mapping(target = "usuarioAnulacionNombreCompleto",
            expression = "java(nombreCompleto(entity.getUsuarioAnulacion()))")
    CompraDto toResumen(Compra entity);

    @IterableMapping(qualifiedByName = "resumen")
    List<CompraDto> toResumenList(List<Compra> entities);

    /** "Nombre Apellido" del usuario, o null si no hay usuario. */
    default String nombreCompleto(Usuario usuario) {
        return usuario == null ? null : usuario.getNombre() + " " + usuario.getApellido();
    }

}
