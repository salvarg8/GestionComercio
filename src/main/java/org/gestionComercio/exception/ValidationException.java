package org.gestionComercio.exception;

import lombok.Getter;
import org.gestionComercio.validation.ValidationResult;

@Getter
public class ValidationException extends BusinessException {

    private final ValidationResult validationResult;

    public ValidationException(ValidationResult validationResult) {
        super("Se encontraron errores de validación.");
        this.validationResult = validationResult;
    }
}