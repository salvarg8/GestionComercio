package org.gestionComercio.validation;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.repository.ClienteRepository;
import org.gestionComercio.util.CuitUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Espera el DTO ya normalizado por el servicio (textos recortados, opcionales
 * vacíos en null y documento sin guiones, puntos ni espacios).
 */
@Component
@RequiredArgsConstructor
public class ClienteValidator implements Validator<ClienteDto> {

    public static final int NOMBRE_MAX = 150;
    public static final int APELLIDO_MAX = 100;
    public static final int DOCUMENTO_MAX = 20;
    public static final int TELEFONO_MAX = 50;
    public static final int EMAIL_MAX = 100;
    public static final int DIRECCION_MAX = 150;

    private final ClienteRepository clienteRepository;

    @Override
    public ValidationResult validate(ClienteDto dto) {

        ValidationResult result = new ValidationResult();

        validarNombre(dto, result);
        validarDocumento(dto, result);
        validarCondicionIva(dto, result);
        validarEmail(dto, result);

        largoMaximo(dto.getTelefono(), TELEFONO_MAX, "telefono", "El teléfono", result);
        largoMaximo(dto.getDireccion(), DIRECCION_MAX, "direccion", "La dirección", result);

        return result;
    }

    /**
     * El nombre (o razón social) es siempre obligatorio. El apellido también,
     * salvo para clientes con CUIT, que pueden ser empresas.
     */
    private void validarNombre(ClienteDto dto, ValidationResult result) {

        boolean puedeSerEmpresa = dto.getTipoDocumento() == TipoDocumento.CUIT;

        ValidationUtils.required(
                dto.getNombre(),
                "nombre",
                puedeSerEmpresa
                        ? "El nombre o razón social es obligatorio."
                        : "El nombre es obligatorio.",
                result
        );

        largoMaximo(dto.getNombre(), NOMBRE_MAX, "nombre", "El nombre", result);

        if (!puedeSerEmpresa) {
            ValidationUtils.required(
                    dto.getApellido(),
                    "apellido",
                    "El apellido es obligatorio.",
                    result
            );
        }

        largoMaximo(dto.getApellido(), APELLIDO_MAX, "apellido", "El apellido", result);
    }

    private void validarDocumento(ClienteDto dto, ValidationResult result) {

        ValidationUtils.notNull(
                dto.getTipoDocumento(),
                "tipoDocumento",
                "El tipo de documento es obligatorio.",
                result
        );

        String numero = dto.getNumeroDocumento();

        if (numero == null || numero.isBlank()) {
            result.addError("numeroDocumento", "El número de documento es obligatorio.");
            return;
        }

        if (dto.getTipoDocumento() == null) {
            return;
        }

        boolean formatoValido = switch (dto.getTipoDocumento()) {
            case DNI -> validarDni(numero, result);
            case CUIT, CUIL -> validarCuitCuil(dto.getTipoDocumento(), numero, result);
            case PASAPORTE -> validarPasaporte(numero, result);
        };

        if (formatoValido) {
            validarDocumentoDuplicado(dto, result);
        }
    }

    private boolean validarDni(String numero, ValidationResult result) {

        if (!numero.matches("\\d{7,8}")) {
            result.addError(
                    "numeroDocumento",
                    "El DNI debe tener 7 u 8 dígitos (por ejemplo, 30123456)."
            );
            return false;
        }

        return true;
    }

    private boolean validarCuitCuil(TipoDocumento tipo, String numero, ValidationResult result) {

        if (!numero.matches("\\d{" + CuitUtils.LARGO + "}")) {
            result.addError(
                    "numeroDocumento",
                    "El " + tipo + " debe tener 11 dígitos (por ejemplo, 20-30123456-7)."
            );
            return false;
        }

        if (!CuitUtils.esValido(numero)) {
            result.addError(
                    "numeroDocumento",
                    "El " + tipo + " no es válido: el dígito verificador no coincide."
            );
            return false;
        }

        return true;
    }

    private boolean validarPasaporte(String numero, ValidationResult result) {

        if (!numero.matches("[A-Z0-9]{6," + DOCUMENTO_MAX + "}")) {
            result.addError(
                    "numeroDocumento",
                    "El pasaporte debe tener entre 6 y " + DOCUMENTO_MAX
                            + " letras o números, sin espacios ni símbolos."
            );
            return false;
        }

        return true;
    }

    private void validarDocumentoDuplicado(ClienteDto dto, ValidationResult result) {

        Optional<Cliente> existente = dto.getId() == null
                ? clienteRepository.findByTipoDocumentoAndNumeroDocumento(
                        dto.getTipoDocumento(), dto.getNumeroDocumento())
                : clienteRepository.findByTipoDocumentoAndNumeroDocumentoAndIdNot(
                        dto.getTipoDocumento(), dto.getNumeroDocumento(), dto.getId());

        existente.ifPresent(cliente -> result.addError(
                "numeroDocumento",
                cliente.getEstadoRegistro() == EstadoRegistro.ACTIVO
                        ? "Ya existe un cliente con ese " + dto.getTipoDocumento() + ": "
                                + descripcion(cliente) + "."
                        : "Ya existe un cliente dado de baja con ese " + dto.getTipoDocumento()
                                + " (" + descripcion(cliente)
                                + "). Puede volver a habilitarlo desde el listado de clientes."
        ));
    }

    /**
     * Responsables inscriptos, monotributistas, exentos y demás sujetos
     * inscriptos se identifican con CUIT (lo exige la factura a nombre del
     * cliente). Consumidor final, no categorizado y del exterior, no.
     */
    private void validarCondicionIva(ClienteDto dto, ValidationResult result) {

        ValidationUtils.notNull(
                dto.getCondicionIva(),
                "condicionIva",
                "La condición frente al IVA es obligatoria.",
                result
        );

        CondicionIVA condicion = dto.getCondicionIva();

        if (condicion != null
                && condicion.requiereCuit()
                && dto.getTipoDocumento() != null
                && dto.getTipoDocumento() != TipoDocumento.CUIT) {

            result.addError(
                    "condicionIva",
                    "Un cliente " + condicion.getDescripcion().toLowerCase() + " debe identificarse con CUIT."
            );
        }
    }

    private void validarEmail(ClienteDto dto, ValidationResult result) {

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            return;
        }

        if (!org.gestionComercio.util.ValidationUtils.isEmail(dto.getEmail())) {
            result.addError("email", "El email no tiene un formato válido.");
            return;
        }

        largoMaximo(dto.getEmail(), EMAIL_MAX, "email", "El email", result);
    }

    private String descripcion(Cliente cliente) {

        return cliente.getApellido() == null || cliente.getApellido().isBlank()
                ? cliente.getNombre()
                : cliente.getApellido() + ", " + cliente.getNombre();
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
