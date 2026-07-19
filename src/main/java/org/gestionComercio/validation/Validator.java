package org.gestionComercio.validation;

public interface Validator<T> {

    ValidationResult validate(T object);

}