package org.gestionComercio.dto.configuracion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.ComprobantePorDefecto;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.FormatoTicket;
import org.gestionComercio.enums.Redondeo;
import org.gestionComercio.enums.UnidadMedida;

/**
 * Parámetros generales del sistema. Al guardar, cada valor null se reemplaza
 * por su valor por defecto.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ConfiguracionDto extends BaseDto {

    /** Largo máximo del texto de pie de los comprobantes. */
    public static final int PIE_MAX = 500;

    /** Si para vender hace falta una caja abierta. */
    private Boolean ventaExigeCaja;

    /** Valor que se propone en "permite stock negativo" al crear un producto. */
    private Boolean stockNegativoPorDefecto;

    /** Unidad de medida que se propone al crear un producto. */
    private UnidadMedida unidadMedidaPorDefecto;

    /** Forma de pago con la que empieza cada venta (una del punto de venta). */
    private FormaPago formaPagoPorDefecto;

    private ComprobantePorDefecto comprobantePorDefecto;

    /** Redondeo del total de las ventas, siempre hacia abajo. */
    private Redondeo redondeo;

    private FormatoTicket formatoTicket;

    /** Si el logo de la empresa se imprime en los comprobantes. */
    private Boolean imprimirLogo;

    /** Texto al pie de los comprobantes; null si no lleva. */
    private String pieComprobante;

    /** Los valores que tiene el sistema recién instalado. */
    public static ConfiguracionDto porDefecto() {

        return ConfiguracionDto.builder()
                .ventaExigeCaja(true)
                .stockNegativoPorDefecto(false)
                .unidadMedidaPorDefecto(UnidadMedida.UNIDAD)
                .formaPagoPorDefecto(FormaPago.EFECTIVO)
                .comprobantePorDefecto(ComprobantePorDefecto.FACTURA)
                .redondeo(Redondeo.SIN_REDONDEO)
                .formatoTicket(FormatoTicket.porDefecto())
                .imprimirLogo(true)
                .build();
    }

    /** @return true si para vender hace falta una caja abierta (null = sí) */
    public boolean exigeCaja() {
        return !Boolean.FALSE.equals(ventaExigeCaja);
    }
}
