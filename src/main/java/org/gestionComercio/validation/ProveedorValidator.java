package org.gestionComercio.validation;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.repository.ProveedorRepository;
import org.gestionComercio.util.CuitUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Espera el DTO ya normalizado por el servicio (textos recortados, opcionales
 * vacíos en null y CUIT sin guiones).
 */
@Component
@RequiredArgsConstructor
public class ProveedorValidator implements Validator<ProveedorDto> {

    public static final int RAZON_SOCIAL_MAX = 150;
    public static final int TELEFONO_MAX = 50;
    public static final int EMAIL_MAX = 100;
    public static final int DIRECCION_MAX = 150;
    public static final int OBSERVACIONES_MAX = 500;

    private final ProveedorRepository proveedorRepository;

    @Override
    public ValidationResult validate(ProveedorDto dto) {

        ValidationResult result = new ValidationResult();

        validarRazonSocial(dto, result);
        validarCuit(dto, result);
        validarEmail(dto, result);

        largoMaximo(dto.getTelefono(), TELEFONO_MAX, "telefono", "El teléfono", result);
        largoMaximo(dto.getDireccion(), DIRECCION_MAX, "direccion", "La dirección", result);
        largoMaximo(dto.getObservaciones(), OBSERVACIONES_MAX, "observaciones",
                "Las observaciones", result);

        return result;
    }

    private void validarRazonSocial(ProveedorDto dto, ValidationResult result) {

        ValidationUtils.required(
                dto.getRazonSocial(),
                "razonSocial",
                "La razón social o nombre es obligatoria.",
                result
        );

        largoMaximo(dto.getRazonSocial(), RAZON_SOCIAL_MAX, "razonSocial",
                "La razón social", result);
    }

    /** El CUIT es opcional; si se informa, tiene que ser válido y no estar repetido. */
    private void validarCuit(ProveedorDto dto, ValidationResult result) {

        if (dto.getCuit() == null || dto.getCuit().isBlank()) {
            return;
        }

        if (!dto.getCuit().matches("\\d{" + CuitUtils.LARGO + "}")) {
            result.addError(
                    "cuit",
                    "El CUIT debe tener 11 dígitos (por ejemplo, 30-71234567-1)."
            );
            return;
        }

        if (!CuitUtils.esValido(dto.getCuit())) {
            result.addError(
                    "cuit",
                    "El CUIT no es válido: el dígito verificador no coincide."
            );
            return;
        }

        Optional<Proveedor> existente = dto.getId() == null
                ? proveedorRepository.findByCuit(dto.getCuit())
                : proveedorRepository.findByCuitAndIdNot(dto.getCuit(), dto.getId());

        existente.ifPresent(proveedor -> result.addError(
                "cuit",
                proveedor.getEstadoRegistro() == EstadoRegistro.ACTIVO
                        ? "Ya existe un proveedor con ese CUIT: " + proveedor.getRazonSocial() + "."
                        : "Ya existe un proveedor dado de baja con ese CUIT ("
                                + proveedor.getRazonSocial()
                                + "). Puede volver a habilitarlo desde el listado de proveedores."
        ));
    }

    private void validarEmail(ProveedorDto dto, ValidationResult result) {

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
