package org.gestionComercio.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.gestionComercio.enums.PermisoCodigo;

/**
 * Guarda PermisoCodigo como texto.
 *
 * Se usa en lugar de @Enumerated(EnumType.STRING) porque con @Enumerated
 * Hibernate genera un CHECK (codigo in (...)) con los valores del enum al
 * momento de crear la tabla, y con ddl-auto=update ese CHECK nunca se
 * actualiza: agregar un permiso nuevo al enum rompía el arranque.
 */
@Converter
public class PermisoCodigoConverter implements AttributeConverter<PermisoCodigo, String> {

    @Override
    public String convertToDatabaseColumn(PermisoCodigo codigo) {
        return codigo == null ? null : codigo.name();
    }

    @Override
    public PermisoCodigo convertToEntityAttribute(String valor) {
        return valor == null ? null : PermisoCodigo.valueOf(valor);
    }
}
