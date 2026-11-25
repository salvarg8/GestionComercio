package org.gestionComercio.validation;

import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.util.CuitUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Espera el DTO ya normalizado por el servicio (textos recortados, opcionales
 * vacíos en null y CUIT sin guiones, puntos ni espacios).
 */
@Component
public class EmpresaValidator implements Validator<EmpresaDto> {

    public static final int RAZON_SOCIAL_MAX = 150;
    public static final int NOMBRE_FANTASIA_MAX = 150;
    public static final int INGRESOS_BRUTOS_MAX = 30;
    public static final int DIRECCION_MAX = 150;
    public static final int TELEFONO_MAX = 50;
    public static final int EMAIL_MAX = 100;
    public static final int LOGO_MAX = 255;

    /** Mayor importe que admite una columna precision 12, scale 2. */
    public static final BigDecimal MONTO_MAXIMO_CAJA_TOPE = new BigDecimal("9999999999.99");

    @Override
    public ValidationResult validate(EmpresaDto dto) {

        ValidationResult result = new ValidationResult();

        ValidationUtils.required(
                dto.getRazonSocial(),
                "razonSocial",
                "La razón social es obligatoria.",
                result
        );

        largoMaximo(dto.getRazonSocial(), RAZON_SOCIAL_MAX, "razonSocial", "La razón social", result);
        largoMaximo(dto.getNombreFantasia(), NOMBRE_FANTASIA_MAX, "nombreFantasia", "El nombre de fantasía", result);

        validarCuit(dto, result);
        validarCondicionIva(dto, result);
        validarEmail(dto, result);

        if (dto.getFechaInicioActividades() != null && dto.getFechaInicioActividades().isAfter(LocalDate.now())) {
            result.addError("fechaInicioActividades", "La fecha de inicio de actividades no puede ser futura.");
        }

        largoMaximo(dto.getIngresosBrutos(), INGRESOS_BRUTOS_MAX, "ingresosBrutos", "Ingresos brutos", result);
        largoMaximo(dto.getDireccion(), DIRECCION_MAX, "direccion", "La dirección", result);
        largoMaximo(dto.getTelefono(), TELEFONO_MAX, "telefono", "El teléfono", result);
        largoMaximo(dto.getLogo(), LOGO_MAX, "logo", "La ruta del logo", result);

        validarControlEfectivo(dto, result);

        return result;
    }

    /**
     * Monto máximo en caja: opcional, mayor que cero y con hasta dos decimales.
     * Clientes antes de bloquear: opcional, cero o más, y sólo con monto máximo.
     */
    private void validarControlEfectivo(EmpresaDto dto, ValidationResult result) {

        BigDecimal monto = dto.getMontoMaximoCaja();

        if (monto != null) {

            if (monto.signum() <= 0) {
                result.addError("montoMaximoCaja", "El monto máximo en caja debe ser mayor que cero.");
            } else if (monto.stripTrailingZeros().scale() > 2) {
                result.addError("montoMaximoCaja", "El monto máximo en caja admite hasta dos decimales.");
            } else if (monto.compareTo(MONTO_MAXIMO_CAJA_TOPE) > 0) {
                result.addError("montoMaximoCaja", "El monto máximo en caja supera el máximo permitido.");
            }
        }

        Integer clientes = dto.getClientesAntesDeBloqueo();

        if (clientes == null) {
            return;
        }

        if (monto == null) {
            result.addError("clientesAntesDeBloqueo",
                    "Para bloquear la caja primero indique el monto máximo en caja.");
        } else if (clientes < 0) {
            result.addError("clientesAntesDeBloqueo", "La cantidad de clientes no puede ser negativa.");
        }
    }

    private void validarCuit(EmpresaDto dto, ValidationResult result) {

        String cuit = dto.getCuit();

        if (cuit == null || cuit.isBlank()) {
            result.addError("cuit", "El CUIT es obligatorio.");
            return;
        }

        if (!cuit.matches("\\d{" + CuitUtils.LARGO + "}")) {
            result.addError("cuit", "El CUIT debe tener 11 dígitos (por ejemplo, 30-71234567-1).");
            return;
        }

        if (!CuitUtils.esValido(cuit)) {
            result.addError("cuit", "El CUIT no es válido: el dígito verificador no coincide.");
        }
    }

    /**
     * El comercio emite comprobantes: sólo puede ser responsable inscripto,
     * monotributista o exento (define si factura A/B o C).
     */
    private void validarCondicionIva(EmpresaDto dto, ValidationResult result) {

        ValidationUtils.notNull(
                dto.getCondicionIva(),
                "condicionIva",
                "La condición frente al IVA es obligatoria.",
                result
        );

        CondicionIVA condicion = dto.getCondicionIva();

        if (condicion != null && !condicion.esDeEmisor()) {
            result.addError(
                    "condicionIva",
                    "La empresa no puede ser " + condicion.getDescripcion().toLowerCase()
                            + ": elija responsable inscripto, monotributista o exento."
            );
        }
    }

    private void validarEmail(EmpresaDto dto, ValidationResult result) {

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            return;
        }

        if (!org.gestionComercio.util.ValidationUtils.isEmail(dto.getEmail())) {
            result.addError("email", "El email no tiene un formato válido.");
            return;
        }

        largoMaximo(dto.getEmail(), EMAIL_MAX, "email", "El email", result);
    }

    private void largoMaximo(
            String valor,
            int maximo,
            String campo,
            String nombreCampo,
            ValidationResult result) {

        if (valor != null && valor.length() > maximo) {
            result.addError(
                    campo,
                    nombreCampo + " no puede superar los " + maximo + " caracteres."
            );
        }
    }
}
