package org.gestionComercio.dto.empresa;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.dto.BaseDto;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.util.CuitUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos del comercio que se imprimen en comprobantes y reportes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class EmpresaDto extends BaseDto {

    private String razonSocial;

    private String nombreFantasia;

    /** Al guardar se normaliza: sin guiones, puntos ni espacios. */
    private String cuit;

    private String ingresosBrutos;

    private CondicionIVA condicionIva;

    /** Se imprime en los comprobantes electrónicos. */
    private LocalDate fechaInicioActividades;

    private String direccion;

    private String telefono;

    private String email;

    /** Ruta del archivo de imagen del logo. */
    private String logo;

    /** Saldo de caja a partir del cual hay que retirar dinero; null = sin control. */
    private BigDecimal montoMaximoCaja;

    /** Ventas permitidas una vez alcanzado el monto máximo; null = nunca se bloquea. */
    private Integer clientesAntesDeBloqueo;

    /** Si los retiros de caja los debe confirmar un encargado (CAJA_CONFIRMAR_RETIRO). */
    private boolean retiroRequiereConfirmacion;

    /** CUIT con guiones (XX-XXXXXXXX-X). */
    public String getCuitFormateado() {
        return CuitUtils.formatear(cuit);
    }

    /** El nombre de fantasía si está cargado; si no, la razón social. */
    public String getNombreComercial() {

        return nombreFantasia == null || nombreFantasia.isBlank()
                ? razonSocial
                : nombreFantasia;
    }
}
