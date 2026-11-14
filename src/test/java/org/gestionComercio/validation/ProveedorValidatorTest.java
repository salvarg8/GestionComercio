package org.gestionComercio.validation;

import org.gestionComercio.dto.proveedor.ProveedorDto;
import org.gestionComercio.entity.Proveedor;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.repository.ProveedorRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProveedorValidatorTest {

    private static final String CUIT_VALIDO = "30712345671";

    @Mock
    private ProveedorRepository proveedorRepository;

    @InjectMocks
    private ProveedorValidator validator;

    private ProveedorDto dto(Long id, String razonSocial, String cuit) {
        return ProveedorDto.builder().id(id).razonSocial(razonSocial).cuit(cuit).build();
    }

    private boolean tieneError(ValidationResult result, String campo, String texto) {
        return result.getErrors().stream()
                .anyMatch(e -> e.getField().equals(campo) && e.getMessage().contains(texto));
    }

    @Test
    @DisplayName("un proveedor válido con CUIT no tiene errores")
    void validoConCuit() {
        when(proveedorRepository.findByCuit(CUIT_VALIDO)).thenReturn(Optional.empty());

        ProveedorDto dto = dto(null, "Distribuidora del Centro", CUIT_VALIDO);
        dto.setEmail("ventas@distribuidora.com");

        assertFalse(validator.validate(dto).hasErrors());
    }

    @Test
    @DisplayName("el CUIT es opcional: sin CUIT no consulta duplicados")
    void sinCuit() {
        assertFalse(validator.validate(dto(null, "Almacén Don Pepe", null)).hasErrors());

        verify(proveedorRepository, never()).findByCuit(any());
    }

    @Test
    @DisplayName("la razón social es obligatoria")
    void razonSocialObligatoria() {
        assertTrue(tieneError(validator.validate(dto(null, "  ", null)), "razonSocial", "obligatoria"));
    }

    @Test
    @DisplayName("la razón social no puede superar el máximo")
    void razonSocialLarga() {
        String larga = "x".repeat(ProveedorValidator.RAZON_SOCIAL_MAX + 1);

        assertTrue(tieneError(validator.validate(dto(null, larga, null)), "razonSocial", "superar"));
    }

    @Test
    @DisplayName("el CUIT debe tener 11 dígitos")
    void cuitFormato() {
        assertTrue(tieneError(validator.validate(dto(null, "X", "3071234567")), "cuit", "11 dígitos"));
        assertTrue(tieneError(validator.validate(dto(null, "X", "30A12345671")), "cuit", "11 dígitos"));
    }

    @Test
    @DisplayName("el CUIT debe tener dígito verificador correcto")
    void cuitVerificador() {
        assertTrue(tieneError(validator.validate(dto(null, "X", "30712345672")), "cuit", "verificador"));
    }

    @Test
    @DisplayName("no permite repetir el CUIT de un proveedor activo")
    void cuitDuplicadoActivo() {
        when(proveedorRepository.findByCuit(CUIT_VALIDO))
                .thenReturn(Optional.of(Proveedor.builder().razonSocial("Otro S.A.").build()));

        assertTrue(tieneError(validator.validate(dto(null, "Nuevo", CUIT_VALIDO)),
                "cuit", "Ya existe un proveedor con ese CUIT"));
    }

    @Test
    @DisplayName("avisa si el CUIT lo ocupa un proveedor dado de baja")
    void cuitDuplicadoDadoDeBaja() {
        Proveedor inactivo = Proveedor.builder().razonSocial("Otro S.A.").build();
        inactivo.setEstadoRegistro(EstadoRegistro.INACTIVO);

        when(proveedorRepository.findByCuit(CUIT_VALIDO)).thenReturn(Optional.of(inactivo));

        assertTrue(tieneError(validator.validate(dto(null, "Nuevo", CUIT_VALIDO)),
                "cuit", "dado de baja"));
    }

    @Test
    @DisplayName("al editar, excluye al propio proveedor de la verificación de duplicados")
    void edicionExcluyeAlMismo() {
        when(proveedorRepository.findByCuitAndIdNot(CUIT_VALIDO, 1L)).thenReturn(Optional.empty());

        assertFalse(validator.validate(dto(1L, "Distribuidora", CUIT_VALIDO)).hasErrors());
    }

    @Test
    @DisplayName("el email, si se informa, debe tener formato válido")
    void emailInvalido() {
        ProveedorDto dto = dto(null, "Distribuidora", null);
        dto.setEmail("ventas.distribuidora.com");

        assertTrue(tieneError(validator.validate(dto), "email", "formato"));
    }

    @Test
    @DisplayName("las observaciones no pueden superar el máximo")
    void observacionesLargas() {
        ProveedorDto dto = dto(null, "Distribuidora", null);
        dto.setObservaciones("x".repeat(ProveedorValidator.OBSERVACIONES_MAX + 1));

        assertTrue(tieneError(validator.validate(dto), "observaciones", "superar"));
    }
}
