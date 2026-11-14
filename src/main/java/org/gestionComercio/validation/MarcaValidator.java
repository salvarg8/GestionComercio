package org.gestionComercio.validation;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.marca.MarcaDto;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.repository.MarcaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MarcaValidator implements Validator<MarcaDto> {

    public static final int NOMBRE_MAX = 80;
    public static final int DESCRIPCION_MAX = 255;

    private final MarcaRepository marcaRepository;

    @Override
    public ValidationResult validate(MarcaDto dto) {

        ValidationResult result = new ValidationResult();

        validarNombre(dto, result);
        validarDescripcion(dto, result);

        return result;
    }

    private void validarNombre(MarcaDto dto, ValidationResult result) {

        ValidationUtils.required(
                dto.getNombre(),
                "nombre",
                "El nombre es obligatorio.",
                result
        );

        if (dto.getNombre() == null || dto.getNombre().isBlank()) {
            return;
        }

        if (dto.getNombre().length() > NOMBRE_MAX) {
            result.addError(
                    "nombre",
                    "El nombre no puede superar los " + NOMBRE_MAX + " caracteres."
            );
        }

        Optional<Marca> existente = dto.getId() == null
                ? marcaRepository.findByNombreIgnoreCase(dto.getNombre())
                : marcaRepository.findByNombreIgnoreCaseAndIdNot(dto.getNombre(), dto.getId());

        existente.ifPresent(marca -> result.addError(
                "nombre",
                marca.getEstadoRegistro() == EstadoRegistro.ACTIVO
                        ? "Ya existe una marca con ese nombre."
                        : "Ya existe una marca dada de baja con ese nombre. Puede volver a habilitarla desde el listado de marcas."
        ));
    }

    private void validarDescripcion(MarcaDto dto, ValidationResult result) {

        if (dto.getDescripcion() != null
                && dto.getDescripcion().length() > DESCRIPCION_MAX) {

            result.addError(
                    "descripcion",
                    "La descripción no puede superar los " + DESCRIPCION_MAX + " caracteres."
            );
        }
    }
}
