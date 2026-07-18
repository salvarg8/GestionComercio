package org.gestionComercio.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {

    private final List<ValidationError> errors = new ArrayList<>();

    public void addError(String field, String message) {
        errors.add(new ValidationError(field, message));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public String getMessage() {

        return errors.stream()
                .map(ValidationError::getMessage)
                .reduce((a, b) -> a + "\n• " + b)
                .map(msg -> "• " + msg)
                .orElse("");

    }

}