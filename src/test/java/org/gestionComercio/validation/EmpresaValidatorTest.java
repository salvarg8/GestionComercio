package org.gestionComercio.validation;

import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.enums.CondicionIVA;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmpresaValidatorTest {

    private static final String CUIT_VALIDO = "30712345671";

    private final EmpresaValidator validator = new EmpresaValidator();

    private EmpresaDto dtoValido() {
        return EmpresaDto.builder()
                .razonSocial("Distribuidora del Centro SA")
                .nombreFantasia("El Centro")
                .cuit(CUIT_VALIDO)
                .ingresosBrutos("901-123456-7")
                .condicionIva(CondicionIVA.RESPONSABLE_INSCRIPTO)
                .direccion("Av. Colón 1234")
                .telefono("3511234567")
                .email("ventas@elcentro.com")
                .logo("C:/logos/logo.png")
                .build();
    }

    private boolean tieneError(ValidationResult result, String campo, String texto) {
        return result.getErrors().stream()
                .anyMatch(e -> e.getField().equals(campo) && e.getMessage().contains(texto));
    }

    @Test
    @DisplayName("una empresa con todos los datos correctos no tiene errores")
    void empresaValida() {
        assertFalse(validator.validate(dtoValido()).hasErrors());
    }

    @Test
    @DisplayName("sólo con los obligatorios (razón social, CUIT, condición IVA) es válida")
    void soloObligatorios() {
        EmpresaDto dto = EmpresaDto.builder()
                .razonSocial("Comercio SRL")
                .cuit(CUIT_VALIDO)
                .condicionIva(CondicionIVA.MONOTRIBUTISTA)
                .build();

        assertFalse(validator.validate(dto).hasErrors());
    }

    @Test
    @DisplayName("la fecha de inicio de actividades no puede ser futura")
    void inicioActividadesFutura() {
        EmpresaDto dto = dtoValido();

        dto.setFechaInicioActividades(java.time.LocalDate.now().minusYears(3));
        assertFalse(validator.validate(dto).hasErrors());

        dto.setFechaInicioActividades(java.time.LocalDate.now().plusDays(1));
        assertTrue(tieneError(validator.validate(dto), "fechaInicioActividades", "futura"));
    }

    @Test
    @DisplayName("la razón social es obligatoria")
    void razonSocialObligatoria() {
        EmpresaDto dto = dtoValido();
        dto.setRazonSocial("  ");

        assertTrue(tieneError(validator.validate(dto), "razonSocial", "obligatoria"));
    }

    @Test
    @DisplayName("el CUIT es obligatorio")
    void cuitObligatorio() {
        EmpresaDto dto = dtoValido();
        dto.setCuit(null);

        assertTrue(tieneError(validator.validate(dto), "cuit", "obligatorio"));
    }

    @Test
    @DisplayName("el CUIT debe tener 11 dígitos")
    void cuitLargoIncorrecto() {
        EmpresaDto dto = dtoValido();
        dto.setCuit("3071234567");

        assertTrue(tieneError(validator.validate(dto), "cuit", "11 dígitos"));
    }

    @Test
    @DisplayName("el CUIT no admite letras")
    void cuitConLetras() {
        EmpresaDto dto = dtoValido();
        dto.setCuit("3071234567A");

        assertTrue(tieneError(validator.validate(dto), "cuit", "11 dígitos"));
    }

    @Test
    @DisplayName("el CUIT con dígito verificador incorrecto no es válido")
    void cuitVerificadorIncorrecto() {
        EmpresaDto dto = dtoValido();
        dto.setCuit("30712345672");

        assertTrue(tieneError(validator.validate(dto), "cuit", "dígito verificador"));
    }

    @Test
    @DisplayName("la condición frente al IVA es obligatoria")
    void condicionIvaObligatoria() {
        EmpresaDto dto = dtoValido();
        dto.setCondicionIva(null);

        assertTrue(tieneError(validator.validate(dto), "condicionIva", "obligatoria"));
    }

    @Test
    @DisplayName("la empresa no puede ser consumidor final")
    void noConsumidorFinal() {
        EmpresaDto dto = dtoValido();
        dto.setCondicionIva(CondicionIVA.CONSUMIDOR_FINAL);

        assertTrue(tieneError(validator.validate(dto), "condicionIva", "consumidor final"));
    }

    @Test
    @DisplayName("el email debe tener un formato válido")
    void emailInvalido() {
        EmpresaDto dto = dtoValido();
        dto.setEmail("ventas.elcentro.com");

        assertTrue(tieneError(validator.validate(dto), "email", "formato"));
    }

    @Test
    @DisplayName("se controlan los largos máximos de los campos")
    void largosMaximos() {
        EmpresaDto dto = dtoValido();
        dto.setRazonSocial("a".repeat(EmpresaValidator.RAZON_SOCIAL_MAX + 1));
        dto.setNombreFantasia("a".repeat(EmpresaValidator.NOMBRE_FANTASIA_MAX + 1));
        dto.setIngresosBrutos("1".repeat(EmpresaValidator.INGRESOS_BRUTOS_MAX + 1));
        dto.setDireccion("a".repeat(EmpresaValidator.DIRECCION_MAX + 1));
        dto.setTelefono("1".repeat(EmpresaValidator.TELEFONO_MAX + 1));
        dto.setLogo("a".repeat(EmpresaValidator.LOGO_MAX + 1));

        ValidationResult result = validator.validate(dto);

        assertTrue(tieneError(result, "razonSocial", "150"));
        assertTrue(tieneError(result, "nombreFantasia", "150"));
        assertTrue(tieneError(result, "ingresosBrutos", "30"));
        assertTrue(tieneError(result, "direccion", "150"));
        assertTrue(tieneError(result, "telefono", "50"));
        assertTrue(tieneError(result, "logo", "255"));
    }

    @Test
    @DisplayName("control del efectivo: el monto máximo es opcional, mayor que cero y con hasta dos decimales")
    void montoMaximoCaja() {
        EmpresaDto dto = dtoValido();

        dto.setMontoMaximoCaja(new BigDecimal("150000.50"));
        assertFalse(validator.validate(dto).hasErrors());

        dto.setMontoMaximoCaja(BigDecimal.ZERO);
        assertTrue(tieneError(validator.validate(dto), "montoMaximoCaja", "mayor que cero"));

        dto.setMontoMaximoCaja(new BigDecimal("100.001"));
        assertTrue(tieneError(validator.validate(dto), "montoMaximoCaja", "dos decimales"));

        dto.setMontoMaximoCaja(new BigDecimal("10000000000"));
        assertTrue(tieneError(validator.validate(dto), "montoMaximoCaja", "máximo permitido"));
    }

    @Test
    @DisplayName("control del efectivo: los clientes antes de bloquear necesitan monto máximo y admiten cero")
    void clientesAntesDeBloqueo() {
        EmpresaDto dto = dtoValido();

        dto.setClientesAntesDeBloqueo(3);
        assertTrue(tieneError(validator.validate(dto), "clientesAntesDeBloqueo", "monto máximo"));

        dto.setMontoMaximoCaja(new BigDecimal("50000"));
        assertFalse(validator.validate(dto).hasErrors());

        dto.setClientesAntesDeBloqueo(0);
        assertFalse(validator.validate(dto).hasErrors());

        dto.setClientesAntesDeBloqueo(-1);
        assertTrue(tieneError(validator.validate(dto), "clientesAntesDeBloqueo", "negativa"));
    }
}
