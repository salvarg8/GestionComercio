package org.gestionComercio.mapper;

import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.dto.arca.IvaComprobanteArcaDto;
import org.gestionComercio.dto.venta.VentaDto;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.entity.ComprobanteArca;
import org.gestionComercio.entity.IvaComprobanteArca;
import org.gestionComercio.entity.Venta;
import org.gestionComercio.enums.AlicuotaIVA;
import org.gestionComercio.enums.TipoComprobante;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/** Sólo de entidad a DTO: los comprobantes los crea y actualiza la facturación electrónica. */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ComprobanteArcaMapper {

    @Mapping(target = "ventaId", source = "venta.id")
    @Mapping(target = "ventaFecha", source = "venta.fecha")
    @Mapping(target = "clienteNombre", expression = "java(nombreCliente(entity.getVenta()))")
    @Mapping(target = "comprobanteAsociado", expression = "java(comprobante(entity.getComprobanteAsociado()))")
    ComprobanteArcaDto toDto(ComprobanteArca entity);

    List<ComprobanteArcaDto> toDtoList(List<ComprobanteArca> entities);

    default IvaComprobanteArcaDto toDto(IvaComprobanteArca iva) {
        return iva == null ? null : new IvaComprobanteArcaDto(
                iva.getAlicuotaArca(),
                AlicuotaIVA.deCodigoArca(iva.getAlicuotaArca()),
                iva.getBaseImponible(),
                iva.getImporte());
    }

    /** Como en el listado de ventas: "Apellido, Nombre", la razón social o consumidor final. */
    default String nombreCliente(Venta venta) {

        if (venta == null) {
            return null;
        }

        Cliente cliente = venta.getCliente();

        if (cliente == null) {
            return VentaDto.CONSUMIDOR_FINAL;
        }

        return cliente.getApellido() == null || cliente.getApellido().isBlank()
                ? cliente.getNombre()
                : cliente.getApellido() + ", " + cliente.getNombre();
    }

    default String comprobante(ComprobanteArca c) {
        return c == null ? null : TipoComprobante.formatear(c.getTipoComprobante(), c.getPuntoVenta(), c.getNumero());
    }

}
