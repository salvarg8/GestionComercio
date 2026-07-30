package org.gestionComercio.validation;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.rol.RolDto;
import org.gestionComercio.repository.RolRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RolValidator implements Validator<RolDto> {

    private final RolRepository rolRepository;

    @Override
    public ValidationResult validate(RolDto dto) {

        ValidationResult result = new ValidationResult();

        validarNombre(dto, result);
        validarDescripcion(dto, result);
        validarPermisos(dto, result);

        return result;
    }

    private void validarNombre(RolDto dto, ValidationResult result) {

        ValidationUtils.required(
                dto.getNombre(),
                "nombre",
                "El nombre es obligatorio.",
                result
        );

        if (dto.getNombre() == null || dto.getNombre().isBlank()) {
            return;
        }

        if (dto.getNombre().length() > 50) {
            result.addError(
                    "nombre",
                    "El nombre no puede superar los 50 caracteres."
            );
        }

        boolean existe = dto.getId() == null
                ? rolRepository.existsByNombreIgnoreCase(dto.getNombre())
                : rolRepository.existsByNombreIgnoreCaseAndIdNot(
                dto.getNombre(),
                dto.getId());

        if (existe) {
            result.addError(
                    "nombre",
                    "Ya existe un rol con ese nombre."
            );
        }
    }

    private void validarDescripcion(RolDto dto, ValidationResult result) {

        if (dto.getDescripcion() != null
                && dto.getDescripcion().length() > 255) {

            result.addError(
                    "descripcion",
                    "La descripción no puede superar los 255 caracteres."
            );
        }
    }

    private void validarPermisos(RolDto dto, ValidationResult result) {

        ValidationUtils.notNull(
                dto.getPermisoIds(),
                "permisoIds",
                "Debe seleccionar al menos un permiso.",
                result
        );

        if (dto.getPermisoIds() != null
                && dto.getPermisoIds().isEmpty()) {

            result.addError(
                    "permisoIds",
                    "Debe seleccionar al menos un permiso."
            );
        }
    }
}