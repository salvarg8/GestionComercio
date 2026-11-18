package org.gestionComercio.validation;

import org.gestionComercio.dto.cliente.ClienteDto;
import org.gestionComercio.entity.Cliente;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.repository.ClienteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteValidatorTest {

    private static final String CUIT_VALIDO = "30712345671";
    private static final String CUIL_VALIDO = "20301234563";

    @Mock
    private ClienteRepository clienteRepository;

    @InjectMocks
    private ClienteValidator validator;

    private ClienteDto persona(TipoDocumento tipo, String numero) {
        return ClienteDto.builder()
                .nombre("Juan")
                .apellido("Pérez")
                .tipoDocumento(tipo)
                .numeroDocumento(numero)
                .condicionIva(CondicionIVA.CONSUMIDOR_FINAL)
                .build();
    }

    private boolean tieneError(ValidationResult result, String campo, String texto) {
        return result.getErrors().stream()
                .anyMatch(e -> e.getField().equals(campo) && e.getMessage().contains(texto));
    }

    private boolean tieneErrorEn(ValidationResult result, String campo) {
        return result.getErrors().stream().anyMatch(e -> e.getField().equals(campo));
    }

    // ------------------------------------------------------------------
    // Casos válidos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("una persona con DNI y consumidor final no tiene errores")
    void personaConDni() {
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, "30123456"))
                .thenReturn(Optional.empty());

        ClienteDto dto = persona(TipoDocumento.DNI, "30123456");
        dto.setEmail("juan@mail.com");

        assertFalse(validator.validate(dto).hasErrors());
    }

    @Test
    @DisplayName("una empresa con CUIT puede no tener apellido")
    void empresaConCuitSinApellido() {
        ClienteDto dto = ClienteDto.builder()
                .nombre("Distribuidora del Centro SA")
                .tipoDocumento(TipoDocumento.CUIT)
                .numeroDocumento(CUIT_VALIDO)
                .condicionIva(CondicionIVA.RESPONSABLE_INSCRIPTO)
                .build();

        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CUIT, CUIT_VALIDO))
                .thenReturn(Optional.empty());

        assertFalse(validator.validate(dto).hasErrors());
    }

    @Test
    @DisplayName("un CUIL válido no tiene errores")
    void cuilValido() {
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.CUIL, CUIL_VALIDO))
                .thenReturn(Optional.empty());

        assertFalse(validator.validate(persona(TipoDocumento.CUIL, CUIL_VALIDO)).hasErrors());
    }

    @Test
    @DisplayName("un pasaporte alfanumérico no tiene errores")
    void pasaporteValido() {
        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.PASAPORTE, "AAB123456"))
                .thenReturn(Optional.empty());

        assertFalse(validator.validate(persona(TipoDocumento.PASAPORTE, "AAB123456")).hasErrors());
    }

    // ------------------------------------------------------------------
    // Nombre
    // ------------------------------------------------------------------

    @Test
    @DisplayName("el nombre es obligatorio")
    void nombreObligatorio() {
        ClienteDto dto = persona(TipoDocumento.DNI, "30123456");
        dto.setNombre("  ");

        assertTrue(tieneError(validator.validate(dto), "nombre", "obligatorio"));
    }

    @Test
    @DisplayName("el apellido es obligatorio para DNI, CUIL y pasaporte")
    void apellidoObligatorioParaPersonas() {
        for (TipoDocumento tipo : new TipoDocumento[]{
                TipoDocumento.DNI, TipoDocumento.CUIL, TipoDocumento.PASAPORTE}) {

            ClienteDto dto = persona(tipo, "30123456");
            dto.setApellido(null);

            assertTrue(tieneError(validator.validate(dto), "apellido", "obligatorio"),
                    "Debería exigir apellido con " + tipo);
        }
    }

    @Test
    @DisplayName("el nombre no puede superar el máximo")
    void nombreLargo() {
        ClienteDto dto = persona(TipoDocumento.DNI, "30123456");
        dto.setNombre("x".repeat(ClienteValidator.NOMBRE_MAX + 1));

        assertTrue(tieneError(validator.validate(dto), "nombre", "superar"));
    }

    // ------------------------------------------------------------------
    // Documento
    // ------------------------------------------------------------------

    @Test
    @DisplayName("el tipo y el número de documento son obligatorios")
    void documentoObligatorio() {
        ClienteDto dto = persona(null, null);

        ValidationResult result = validator.validate(dto);

        assertTrue(tieneError(result, "tipoDocumento", "obligatorio"));
        assertTrue(tieneError(result, "numeroDocumento", "obligatorio"));
    }

    @Test
    @DisplayName("el DNI debe tener 7 u 8 dígitos")
    void dniFormato() {
        assertTrue(tieneError(validator.validate(persona(TipoDocumento.DNI, "123456")),
                "numeroDocumento", "7 u 8 dígitos"));
        assertTrue(tieneError(validator.validate(persona(TipoDocumento.DNI, "301234567")),
                "numeroDocumento", "7 u 8 dígitos"));
        assertTrue(tieneError(validator.validate(persona(TipoDocumento.DNI, "3012345A")),
                "numeroDocumento", "7 u 8 dígitos"));
    }

    @Test
    @DisplayName("el CUIT/CUIL debe tener 11 dígitos")
    void cuitFormato() {
        assertTrue(tieneError(validator.validate(persona(TipoDocumento.CUIT, "3071234567")),
                "numeroDocumento", "11 dígitos"));
        assertTrue(tieneError(validator.validate(persona(TipoDocumento.CUIL, "20A01234561")),
                "numeroDocumento", "11 dígitos"));
    }

    @Test
    @DisplayName("el CUIT/CUIL con dígito verificador incorrecto no es válido")
    void cuitVerificador() {
        assertTrue(tieneError(validator.validate(persona(TipoDocumento.CUIT, "30712345672")),
                "numeroDocumento", "verificador"));
        assertTrue(tieneError(validator.validate(persona(TipoDocumento.CUIL, "20301234562")),
                "numeroDocumento", "verificador"));

        verify(clienteRepository, never()).findByTipoDocumentoAndNumeroDocumento(any(), any());
    }

    @Test
    @DisplayName("el pasaporte sólo admite letras y números")
    void pasaporteFormato() {
        assertTrue(tieneError(validator.validate(persona(TipoDocumento.PASAPORTE, "AB-123456")),
                "numeroDocumento", "pasaporte"));
        assertTrue(tieneError(validator.validate(persona(TipoDocumento.PASAPORTE, "AB12")),
                "numeroDocumento", "pasaporte"));
    }

    @Test
    @DisplayName("no se puede repetir tipo y número de documento de otro cliente")
    void documentoDuplicado() {
        Cliente existente = Cliente.builder().nombre("Ana").apellido("Gómez").build();

        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, "30123456"))
                .thenReturn(Optional.of(existente));

        assertTrue(tieneError(validator.validate(persona(TipoDocumento.DNI, "30123456")),
                "numeroDocumento", "Gómez, Ana"));
    }

    @Test
    @DisplayName("un duplicado dado de baja sugiere volver a habilitarlo")
    void documentoDuplicadoDadoDeBaja() {
        Cliente existente = Cliente.builder().nombre("Ana").apellido("Gómez").build();
        existente.setEstadoRegistro(EstadoRegistro.INACTIVO);

        when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(TipoDocumento.DNI, "30123456"))
                .thenReturn(Optional.of(existente));

        assertTrue(tieneError(validator.validate(persona(TipoDocumento.DNI, "30123456")),
                "numeroDocumento", "habilitarlo"));
    }

    @Test
    @DisplayName("al editar, el duplicado se busca excluyendo al propio cliente")
    void documentoDuplicadoAlEditar() {
        ClienteDto dto = persona(TipoDocumento.DNI, "30123456");
        dto.setId(5L);

        when(clienteRepository.findByTipoDocumentoAndNumeroDocumentoAndIdNot(
                TipoDocumento.DNI, "30123456", 5L)).thenReturn(Optional.empty());

        assertFalse(validator.validate(dto).hasErrors());
        verify(clienteRepository, never()).findByTipoDocumentoAndNumeroDocumento(any(), any());
    }

    // ------------------------------------------------------------------
    // Condición frente al IVA
    // ------------------------------------------------------------------

    @Test
    @DisplayName("la condición frente al IVA es obligatoria")
    void condicionIvaObligatoria() {
        lenient().when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(any(), any()))
                .thenReturn(Optional.empty());

        ClienteDto dto = persona(TipoDocumento.DNI, "30123456");
        dto.setCondicionIva(null);

        assertTrue(tieneError(validator.validate(dto), "condicionIva", "obligatoria"));
    }

    @Test
    @DisplayName("responsable inscripto, monotributista o exento exigen CUIT")
    void condicionIvaExigeCuit() {
        lenient().when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(any(), any()))
                .thenReturn(Optional.empty());

        for (CondicionIVA condicion : new CondicionIVA[]{
                CondicionIVA.RESPONSABLE_INSCRIPTO, CondicionIVA.MONOTRIBUTISTA, CondicionIVA.EXENTO}) {

            ClienteDto dto = persona(TipoDocumento.DNI, "30123456");
            dto.setCondicionIva(condicion);

            assertTrue(tieneError(validator.validate(dto), "condicionIva", "CUIT"),
                    "Debería exigir CUIT para " + condicion);
        }
    }

    // ------------------------------------------------------------------
    // Contacto
    // ------------------------------------------------------------------

    @Test
    @DisplayName("el email, si se informa, debe tener formato válido")
    void emailInvalido() {
        lenient().when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(any(), any()))
                .thenReturn(Optional.empty());

        ClienteDto dto = persona(TipoDocumento.DNI, "30123456");
        dto.setEmail("no-es-un-email");

        assertTrue(tieneError(validator.validate(dto), "email", "formato"));
    }

    @Test
    @DisplayName("teléfono y dirección no pueden superar el máximo")
    void contactoLargo() {
        lenient().when(clienteRepository.findByTipoDocumentoAndNumeroDocumento(any(), any()))
                .thenReturn(Optional.empty());

        ClienteDto dto = persona(TipoDocumento.DNI, "30123456");
        dto.setTelefono("1".repeat(ClienteValidator.TELEFONO_MAX + 1));
        dto.setDireccion("x".repeat(ClienteValidator.DIRECCION_MAX + 1));

        ValidationResult result = validator.validate(dto);

        assertTrue(tieneErrorEn(result, "telefono"));
        assertTrue(tieneErrorEn(result, "direccion"));
    }
}
