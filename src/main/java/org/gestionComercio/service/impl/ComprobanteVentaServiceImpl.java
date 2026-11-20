package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.entity.Venta;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.mapper.ClienteMapper;
import org.gestionComercio.mapper.EmpresaMapper;
import org.gestionComercio.mapper.VentaMapper;
import org.gestionComercio.report.ComprobanteVentaPdf;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.VentaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ComprobanteVentaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lee la empresa directamente del repositorio: imprimir un comprobante no
 * requiere el permiso de ver la configuración (EMPRESA_VER).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComprobanteVentaServiceImpl implements ComprobanteVentaService {

    private final VentaRepository ventaRepository;
    private final EmpresaRepository empresaRepository;
    private final VentaMapper ventaMapper;
    private final EmpresaMapper empresaMapper;
    private final ClienteMapper clienteMapper;
    private final SesionUsuario sesionUsuario;

    @Override
    public byte[] generarPdf(Long ventaId) {

        sesionUsuario.requerirAlgunPermiso(PermisoCodigo.VENTA_VER, PermisoCodigo.VENTA_CREAR);

        if (ventaId == null) {
            throw new BusinessException("La venta es obligatoria.");
        }

        Venta venta = ventaRepository.findConDetalles(ventaId)
                .orElseThrow(() -> new EntityNotFoundException("No existe una venta con ID: " + ventaId));

        EmpresaDto empresa = empresaRepository.findFirstByOrderByIdAsc()
                .map(empresaMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No hay datos de la empresa cargados. Complételos en Configuración."));

        ClienteDto cliente = venta.getCliente() == null ? null : clienteMapper.toDto(venta.getCliente());

        return ComprobanteVentaPdf.generar(ventaMapper.toDto(venta), empresa, cliente);
    }

}
