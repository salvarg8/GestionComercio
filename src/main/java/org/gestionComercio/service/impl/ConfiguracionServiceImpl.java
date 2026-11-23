package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.configuracion.ConfiguracionDto;
import org.gestionComercio.dto.configuracion.DatosInicialesResultado;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.entity.Configuracion;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.Rubro;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.ConfiguracionMapper;
import org.gestionComercio.repository.CategoriaRepository;
import org.gestionComercio.repository.ConfiguracionRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ConfiguracionService;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConfiguracionServiceImpl implements ConfiguracionService {

    private final ConfiguracionRepository configuracionRepository;
    private final CategoriaRepository categoriaRepository;
    private final ConfiguracionMapper configuracionMapper;
    private final SesionUsuario sesionUsuario;

    @Transactional(readOnly = true)
    @Override
    public ConfiguracionDto vigente() {

        // Si faltara la fila (base dañada), se trabaja con los valores por defecto.
        return configuracionRepository.findFirstByOrderByIdAsc()
                .map(configuracionMapper::toDto)
                .orElseGet(ConfiguracionDto::porDefecto);
    }

    @Transactional(readOnly = true)
    @Override
    public ConfiguracionDto obtener() {

        sesionUsuario.requerirPermiso(PermisoCodigo.CONFIGURACION_VER);

        return configuracionMapper.toDto(buscar());
    }

    @Override
    public ConfiguracionDto actualizar(ConfiguracionDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CONFIGURACION_EDITAR);

        if (dto == null) {
            throw new BusinessException("Debe indicar la configuración.");
        }

        Configuracion configuracion = buscar();

        dto.setId(configuracion.getId());

        normalizar(dto);
        validar(dto);

        configuracionMapper.updateEntity(dto, configuracion);

        return configuracionMapper.toDto(configuracionRepository.save(configuracion));
    }

    @Override
    public DatosInicialesResultado cargarDatosIniciales(Rubro rubro) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CONFIGURACION_EDITAR);

        if (rubro == null) {
            throw new BusinessException("Debe elegir el rubro del comercio.");
        }

        List<String> creadas = new ArrayList<>();
        List<String> existentes = new ArrayList<>();

        for (String nombre : rubro.getCategorias()) {

            // El nombre es único también entre las categorías eliminadas: no se reactivan.
            if (categoriaRepository.findByNombreIgnoreCase(nombre).isPresent()) {
                existentes.add(nombre);
                continue;
            }

            categoriaRepository.save(Categoria.builder().nombre(nombre).build());
            creadas.add(nombre);
        }

        Configuracion configuracion = buscar();
        configuracion.setUnidadMedidaPorDefecto(rubro.getUnidadPorDefecto());
        configuracionRepository.save(configuracion);

        return new DatosInicialesResultado(rubro, creadas, existentes);
    }

    private Configuracion buscar() {

        return configuracionRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new EntityNotFoundException("No está creada la configuración del sistema."));
    }

    /** Completa los valores por defecto y deja el pie recortado (o null si está vacío). */
    static void normalizar(ConfiguracionDto dto) {

        ConfiguracionDto defecto = ConfiguracionDto.porDefecto();

        if (dto.getVentaExigeCaja() == null) {
            dto.setVentaExigeCaja(defecto.getVentaExigeCaja());
        }
        if (dto.getStockNegativoPorDefecto() == null) {
            dto.setStockNegativoPorDefecto(defecto.getStockNegativoPorDefecto());
        }
        if (dto.getUnidadMedidaPorDefecto() == null) {
            dto.setUnidadMedidaPorDefecto(defecto.getUnidadMedidaPorDefecto());
        }
        if (dto.getFormaPagoPorDefecto() == null) {
            dto.setFormaPagoPorDefecto(defecto.getFormaPagoPorDefecto());
        }
        if (dto.getComprobantePorDefecto() == null) {
            dto.setComprobantePorDefecto(defecto.getComprobantePorDefecto());
        }
        if (dto.getRedondeo() == null) {
            dto.setRedondeo(defecto.getRedondeo());
        }
        if (dto.getFormatoTicket() == null) {
            dto.setFormatoTicket(defecto.getFormatoTicket());
        }
        if (dto.getImprimirLogo() == null) {
            dto.setImprimirLogo(defecto.getImprimirLogo());
        }

        String pie = dto.getPieComprobante() == null ? "" : dto.getPieComprobante().strip();
        dto.setPieComprobante(pie.isEmpty() ? null : pie);
    }

    static void validar(ConfiguracionDto dto) {

        ValidationResult result = new ValidationResult();

        if (!Arrays.asList(FormaPago.delPuntoDeVenta()).contains(dto.getFormaPagoPorDefecto())) {
            result.addError("formaPagoPorDefecto",
                    "La forma de pago por defecto tiene que ser una de las del punto de venta.");
        }

        if (dto.getPieComprobante() != null && dto.getPieComprobante().length() > ConfiguracionDto.PIE_MAX) {
            result.addError("pieComprobante",
                    "El texto de pie no puede superar los " + ConfiguracionDto.PIE_MAX + " caracteres.");
        }

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }
    }
}
