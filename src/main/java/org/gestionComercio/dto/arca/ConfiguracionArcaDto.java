package org.gestionComercio.dto.arca;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.gestionComercio.enums.AmbienteArca;
import org.gestionComercio.enums.OrigenClaveArca;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Configuración de la facturación electrónica. Se editan el ambiente, el
 * punto de venta y si está habilitada; el resto describe el certificado.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracionArcaDto {

    /** Días antes del vencimiento del certificado desde los que se avisa que hay que renovarlo. */
    public static final int DIAS_AVISO_VENCIMIENTO = 30;

    private AmbienteArca ambiente;

    private Integer puntoVenta;

    private boolean habilitada;

    // Sólo lectura

    /** false si esta PC no tiene la clave del certificado (ni en la variable de entorno ni guardada). */
    private boolean claveConfigurada;

    /** De dónde toma esta PC la clave del certificado. */
    private OrigenClaveArca origenClave;

    /**
     * Si la clave de esta PC abre el certificado (o el pedido) guardado; null si
     * no hay clave o no hay nada guardado todavía.
     */
    private Boolean claveCorrecta;

    private boolean certificadoCargado;

    private String certificadoTitular;

    private LocalDateTime certificadoVencimiento;

    /** true si se generó un pedido de certificado cuyo certificado todavía no se importó. */
    private boolean pedidoPendiente;

    private LocalDateTime pedidoFecha;

    /** Días que faltan para que venza el certificado (negativo si venció); null sin certificado. */
    public Long getDiasParaVencer() {
        return certificadoVencimiento == null
                ? null
                : Duration.between(LocalDateTime.now(), certificadoVencimiento).toDays();
    }

    /** @return true si el certificado venció o vence dentro de {@link #DIAS_AVISO_VENCIMIENTO} días. */
    public boolean isCertificadoPorVencer() {
        Long dias = getDiasParaVencer();
        return dias != null && dias <= DIAS_AVISO_VENCIMIENTO;
    }

}
