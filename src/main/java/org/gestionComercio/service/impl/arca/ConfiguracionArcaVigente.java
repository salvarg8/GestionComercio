package org.gestionComercio.service.impl.arca;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.arca.ClaveCertificadoArca;
import org.gestionComercio.arca.CertificadosArca;
import org.gestionComercio.arca.CredencialArca;
import org.gestionComercio.entity.ConfiguracionArca;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.repository.ConfiguracionArcaRepository;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.util.CuitUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Lee la configuración de ARCA y los datos de la empresa y controla que
 * alcancen para facturar. No requiere permisos: lo usan los servicios de
 * ventas y de facturación, que ya los controlaron.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConfiguracionArcaVigente {

    private final ConfiguracionArcaRepository configuracionRepository;
    private final EmpresaRepository empresaRepository;
    private final ClaveCertificadoArca claveCertificado;

    /** @return true si la facturación electrónica está habilitada. */
    public boolean habilitada() {
        return configuracionRepository.findFirstByOrderByIdAsc()
                .map(c -> Boolean.TRUE.equals(c.getHabilitada()))
                .orElse(false);
    }

    /**
     * Ambiente, CUIT, punto de venta y condición frente al IVA, sin abrir el
     * certificado (para validar una venta).
     *
     * @throws BusinessException si la facturación no está habilitada o el certificado venció
     */
    public ContextoArca emisor() {
        return contexto(true, false);
    }

    /**
     * Todo lo necesario para llamar a ARCA, con el certificado abierto.
     *
     * @param exigirHabilitada false para probar la conexión antes de habilitarla
     * @throws BusinessException si falta algún dato o no se puede abrir el certificado
     */
    public ContextoArca cargar(boolean exigirHabilitada) {
        return contexto(exigirHabilitada, true);
    }

    private ContextoArca contexto(boolean exigirHabilitada, boolean abrirCertificado) {

        ConfiguracionArca config = configuracionRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new BusinessException(
                        "La facturación electrónica no está configurada. Configúrela en Configuración > ARCA."));

        if (exigirHabilitada && !Boolean.TRUE.equals(config.getHabilitada())) {
            throw new BusinessException("La facturación electrónica no está habilitada.");
        }

        if (config.getPuntoVenta() == null) {
            throw new BusinessException("Falta configurar el punto de venta de facturación electrónica.");
        }

        if (config.getCertificado() == null) {
            throw new BusinessException("Falta importar el certificado de ARCA.");
        }

        if (config.getCertificadoVencimiento() != null
                && config.getCertificadoVencimiento().isBefore(LocalDateTime.now())) {
            throw new BusinessException("El certificado de ARCA venció el "
                    + config.getCertificadoVencimiento().toLocalDate() + ". Genere uno nuevo para seguir facturando.");
        }

        Empresa empresa = empresaRepository.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new BusinessException(
                        "No hay datos de la empresa cargados. Complételos en Configuración."));

        String cuit = CuitUtils.normalizar(empresa.getCuit());

        if (!CuitUtils.esValido(cuit)) {
            throw new BusinessException("El CUIT de la empresa no es válido. Corríjalo en Configuración.");
        }

        if (empresa.getCondicionIva() == null || !empresa.getCondicionIva().esDeEmisor()) {
            throw new BusinessException("La condición frente al IVA de la empresa debe ser responsable inscripto, "
                    + "monotributista o exento.");
        }

        CredencialArca credencial = abrirCertificado
                ? CertificadosArca.leerPkcs12(config.getCertificado(), clave())
                : null;

        return new ContextoArca(config.getAmbiente(), cuit, config.getPuntoVenta(), empresa.getCondicionIva(),
                credencial);
    }

    /**
     * Clave con que se cifra el certificado en la base.
     *
     * @throws BusinessException si no está configurada en esta PC
     */
    public char[] clave() {
        return claveCertificado.obtener().orElseThrow(() -> new BusinessException(
                "Falta cargar en esta PC la clave del certificado de ARCA (en Facturación electrónica, paso 2). "
                        + "Tiene que ser la misma clave en todas las PC."));
    }

}
