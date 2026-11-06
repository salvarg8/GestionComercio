package org.gestionComercio.validation;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.usuario.UsuarioDto;
import org.gestionComercio.repository.UsuarioRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsuarioValidator implements Validator<UsuarioDto> {

    private final UsuarioRepository usuarioRepository;

    @Override
    public ValidationResult validate(UsuarioDto dto) {

        ValidationResult result = new ValidationResult();

        validarUsuario(dto, result);
        validarTexto(dto.getNombre(), "nombre", "El nombre", 100, result);
        validarTexto(dto.getApellido(), "apellido", "El apellido", 100, result);
        validarDocumento(dto, result);
        validarPassword(dto, result);

        ValidationUtils.notNull(dto.getTipoDocumento(), "tipoDocumento",
                "El tipo de documento es obligatorio.", result);

        ValidationUtils.notNull(dto.getEstadoUsuario(), "estadoUsuario",
                "El estado es obligatorio.", result);

        ValidationUtils.notNull(dto.getRolId(), "rolId",
                "El rol es obligatorio.", result);

        return result;
    }

    private void validarUsuario(UsuarioDto dto, ValidationResult result) {

        if (!validarTexto(dto.getUsuario(), "usuario", "El usuario", 50, result)) {
            return;
        }

        if (dto.getUsuario().contains(" ")) {
            result.addError("usuario", "El usuario no puede contener espacios.");
        }

        boolean existe = dto.getId() == null
                ? usuarioRepository.existsByUsuarioIgnoreCase(dto.getUsuario())
                : usuarioRepository.existsByUsuarioIgnoreCaseAndIdNot(dto.getUsuario(), dto.getId());

        if (existe) {
            result.addError("usuario", "Ya existe un usuario con ese nombre de usuario.");
        }
    }

    private void validarDocumento(UsuarioDto dto, ValidationResult result) {

        if (!validarTexto(dto.getDocumento(), "documento", "El documento", 20, result)) {
            return;
        }

        boolean existe = dto.getId() == null
                ? usuarioRepository.existsByDocumento(dto.getDocumento())
                : usuarioRepository.existsByDocumentoAndIdNot(dto.getDocumento(), dto.getId());

        if (existe) {
            result.addError("documento", "Ya existe un usuario con ese documento.");
        }
    }

    private void validarPassword(UsuarioDto dto, ValidationResult result) {

        boolean esAlta = dto.getId() == null;
        boolean informada = dto.getPassword() != null && !dto.getPassword().isEmpty();

        // Al editar, dejarla vacía significa "no cambiarla".
        if (esAlta || informada) {
            PasswordPolicy.validar(dto.getPassword(), "password", result);
        }
    }

    /**
     * Valida obligatoriedad y largo máximo. Devuelve true si el valor está informado.
     */
    private boolean validarTexto(String valor, String field, String etiqueta,
                                 int maximo, ValidationResult result) {

        if (valor == null || valor.isBlank()) {
            result.addError(field, etiqueta + " es obligatorio.");
            return false;
        }

        if (valor.length() > maximo) {
            result.addError(field,
                    etiqueta + " no puede superar los " + maximo + " caracteres.");
        }

        return true;
    }
}
