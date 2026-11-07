package org.gestionComercio.validation;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.categoria.CategoriaDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.repository.CategoriaRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CategoriaValidator implements Validator<CategoriaDto> {

    public static final int NOMBRE_MAX = 80;
    public static final int DESCRIPCION_MAX = 255;

    private final CategoriaRepository categoriaRepository;

    @Override
    public ValidationResult validate(CategoriaDto dto) {

        ValidationResult result = new ValidationResult();

        validarNombre(dto, result);
        validarDescripcion(dto, result);

        return result;
    }

    private void validarNombre(CategoriaDto dto, ValidationResult result) {

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

        Optional<Categoria> existente = dto.getId() == null
                ? categoriaRepository.findByNombreIgnoreCase(dto.getNombre())
                : categoriaRepository.findByNombreIgnoreCaseAndIdNot(dto.getNombre(), dto.getId());

        existente.ifPresent(categoria -> result.addError(
                "nombre",
                categoria.getEstadoRegistro() == EstadoRegistro.ACTIVO
                        ? "Ya existe una categoría con ese nombre."
                        : "Ya existe una categoría eliminada con ese nombre. Use otro nombre."
        ));
    }

    private void validarDescripcion(CategoriaDto dto, ValidationResult result) {

        if (dto.getDescripcion() != null
                && dto.getDescripcion().length() > DESCRIPCION_MAX) {

            result.addError(
                    "descripcion",
                    "La descripción no puede superar los " + DESCRIPCION_MAX + " caracteres."
            );
        }
    }
}
