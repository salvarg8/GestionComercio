package org.gestionComercio.mapper;

import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.Usuario;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * Sólo convierte de entidad a DTO: las cajas se abren y cierran desde
 * {@code CajaService}. Los totales y el saldo esperado de una caja abierta se
 * calculan a partir de los movimientos y los completa el servicio.
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface CajaMapper {

    @Mapping(target = "totalIngresos", ignore = true)
    @Mapping(target = "totalEgresos", ignore = true)
    @Mapping(target = "usuarioId", source = "usuario.id")
    @Mapping(target = "usuarioNombre", source = "usuario.usuario")
    @Mapping(target = "usuarioNombreCompleto", expression = "java(nombreCompleto(entity.getUsuario()))")
    @Mapping(target = "usuarioAperturaId", source = "usuarioApertura.id")
    @Mapping(target = "usuarioAperturaNombre", source = "usuarioApertura.usuario")
    @Mapping(target = "usuarioAperturaNombreCompleto", expression = "java(nombreCompleto(entity.getUsuarioApertura()))")
    @Mapping(target = "usuarioCierreId", source = "usuarioCierre.id")
    @Mapping(target = "usuarioCierreNombre", source = "usuarioCierre.usuario")
    @Mapping(target = "usuarioCierreNombreCompleto", expression = "java(nombreCompleto(entity.getUsuarioCierre()))")
    CajaDto toDto(Caja entity);

    default String nombreCompleto(Usuario usuario) {
        return usuario == null ? null : usuario.getNombre() + " " + usuario.getApellido();
    }

}
