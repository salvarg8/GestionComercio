package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.enums.AnchoTicket;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.EmpresaMapper;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.EmpresaService;
import org.gestionComercio.util.CuitUtils;
import org.gestionComercio.validation.EmpresaValidator;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmpresaServiceImpl implements EmpresaService {

    private final EmpresaRepository empresaRepository;

    private final EmpresaMapper empresaMapper;
    private final EmpresaValidator empresaValidator;
    private final SesionUsuario sesionUsuario;

    @Transactional(readOnly = true)
    @Override
    public EmpresaDto obtener() {

        sesionUsuario.requerirPermiso(PermisoCodigo.EMPRESA_VER);

        return empresaMapper.toDto(buscarEmpresa());
    }

    @Override
    public EmpresaDto actualizar(EmpresaDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.EMPRESA_EDITAR);

        Empresa empresa = buscarEmpresa();

        // Siempre se edita el único registro, venga el ID que venga.
        dto.setId(empresa.getId());

        normalizar(dto);
        validar(dto);

        empresaMapper.updateEntity(dto, empresa);

        return empresaMapper.toDto(empresaRepository.save(empresa));
    }

    private Empresa buscarEmpresa() {

        return empresaRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new EntityNotFoundException(
                        "No hay datos de la empresa cargados."));
    }

    private void validar(EmpresaDto dto) {

        ValidationResult result = empresaValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }
    }

    /**
     * Recorta espacios, deja en null los opcionales vacíos y normaliza el CUIT
     * (sin guiones, puntos ni espacios). Sin ancho de ticket, el de por defecto.
     */
    private void normalizar(EmpresaDto dto) {

        if (dto.getRazonSocial() != null) {
            dto.setRazonSocial(dto.getRazonSocial().trim());
        }

        dto.setNombreFantasia(opcional(dto.getNombreFantasia()));
        dto.setCuit(CuitUtils.normalizar(dto.getCuit()));
        dto.setIngresosBrutos(opcional(dto.getIngresosBrutos()));
        dto.setDireccion(opcional(dto.getDireccion()));
        dto.setTelefono(opcional(dto.getTelefono()));
        dto.setEmail(opcional(dto.getEmail()));
        dto.setLogo(opcional(dto.getLogo()));

        if (dto.getAnchoTicket() == null) {
            dto.setAnchoTicket(AnchoTicket.porDefecto());
        }
    }

    private String opcional(String valor) {

        if (valor == null) {
            return null;
        }

        String recortado = valor.trim();

        return recortado.isEmpty() ? null : recortado;
    }
}
