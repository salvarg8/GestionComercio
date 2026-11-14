package org.gestionComercio.validation;

import org.gestionComercio.dto.marca.MarcaDto;
import org.gestionComercio.entity.Marca;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.repository.MarcaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarcaValidatorTest {

    @Mock
    private MarcaRepository marcaRepository;

    @InjectMocks
    private MarcaValidator validator;

    private MarcaDto dto(Long id, String nombre, String descripcion) {
        return MarcaDto.builder().id(id).nombre(nombre).descripcion(descripcion).build();
    }

    private boolean tieneMensaje(ValidationResult result, String texto) {
        return result.getErrors().stream().anyMatch(e -> e.getMessage().contains(texto));
    }

    @Test
    @DisplayName("una marca válida no tiene errores")
    void valida() {
        when(marcaRepository.findByNombreIgnoreCase("Bebidas")).thenReturn(Optional.empty());

        assertFalse(validator.validate(dto(null, "Bebidas", "Gaseosas y aguas")).hasErrors());
    }

    @Test
    @DisplayName("el nombre es obligatorio")
    void nombreObligatorio() {
        assertTrue(tieneMensaje(validator.validate(dto(null, "  ", null)), "obligatorio"));
    }

    @Test
    @DisplayName("el nombre no puede superar el máximo")
    void nombreLargo() {
        String largo = "x".repeat(MarcaValidator.NOMBRE_MAX + 1);
        when(marcaRepository.findByNombreIgnoreCase(largo)).thenReturn(Optional.empty());

        assertTrue(tieneMensaje(validator.validate(dto(null, largo, null)), "superar"));
    }

    @Test
    @DisplayName("la descripción no puede superar el máximo")
    void descripcionLarga() {
        when(marcaRepository.findByNombreIgnoreCase("Bebidas")).thenReturn(Optional.empty());
        String larga = "x".repeat(MarcaValidator.DESCRIPCION_MAX + 1);

        assertTrue(tieneMensaje(validator.validate(dto(null, "Bebidas", larga)), "descripción"));
    }

    @Test
    @DisplayName("no permite repetir el nombre de una marca activa")
    void nombreDuplicadoActiva() {
        when(marcaRepository.findByNombreIgnoreCase("bebidas"))
                .thenReturn(Optional.of(Marca.builder().nombre("Bebidas").build()));

        assertTrue(tieneMensaje(validator.validate(dto(null, "bebidas", null)), "Ya existe una marca con ese nombre"));
    }

    @Test
    @DisplayName("avisa si el nombre lo ocupa una marca dada de baja")
    void nombreDuplicadoEliminada() {
        Marca eliminada = Marca.builder().nombre("Bebidas").build();
        eliminada.setEstadoRegistro(EstadoRegistro.INACTIVO);

        when(marcaRepository.findByNombreIgnoreCase("Bebidas")).thenReturn(Optional.of(eliminada));

        assertTrue(tieneMensaje(validator.validate(dto(null, "Bebidas", null)), "dada de baja"));
    }

    @Test
    @DisplayName("al editar, excluye la propia marca de la verificación de duplicados")
    void edicionExcluyeAMismo() {
        when(marcaRepository.findByNombreIgnoreCaseAndIdNot("Bebidas", 1L))
                .thenReturn(Optional.empty());

        assertFalse(validator.validate(dto(1L, "Bebidas", null)).hasErrors());
    }
}
