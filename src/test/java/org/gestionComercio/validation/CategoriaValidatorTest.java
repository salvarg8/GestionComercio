package org.gestionComercio.validation;

import org.gestionComercio.dto.categoria.CategoriaDto;
import org.gestionComercio.entity.Categoria;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.repository.CategoriaRepository;
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
class CategoriaValidatorTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaValidator validator;

    private CategoriaDto dto(Long id, String nombre, String descripcion) {
        return CategoriaDto.builder().id(id).nombre(nombre).descripcion(descripcion).build();
    }

    private boolean tieneMensaje(ValidationResult result, String texto) {
        return result.getErrors().stream().anyMatch(e -> e.getMessage().contains(texto));
    }

    @Test
    @DisplayName("una categoría válida no tiene errores")
    void valida() {
        when(categoriaRepository.findByNombreIgnoreCase("Bebidas")).thenReturn(Optional.empty());

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
        String largo = "x".repeat(CategoriaValidator.NOMBRE_MAX + 1);
        when(categoriaRepository.findByNombreIgnoreCase(largo)).thenReturn(Optional.empty());

        assertTrue(tieneMensaje(validator.validate(dto(null, largo, null)), "superar"));
    }

    @Test
    @DisplayName("la descripción no puede superar el máximo")
    void descripcionLarga() {
        when(categoriaRepository.findByNombreIgnoreCase("Bebidas")).thenReturn(Optional.empty());
        String larga = "x".repeat(CategoriaValidator.DESCRIPCION_MAX + 1);

        assertTrue(tieneMensaje(validator.validate(dto(null, "Bebidas", larga)), "descripción"));
    }

    @Test
    @DisplayName("no permite repetir el nombre de una categoría activa")
    void nombreDuplicadoActiva() {
        when(categoriaRepository.findByNombreIgnoreCase("bebidas"))
                .thenReturn(Optional.of(Categoria.builder().nombre("Bebidas").build()));

        assertTrue(tieneMensaje(validator.validate(dto(null, "bebidas", null)), "Ya existe una categoría con ese nombre"));
    }

    @Test
    @DisplayName("avisa si el nombre lo ocupa una categoría eliminada")
    void nombreDuplicadoEliminada() {
        Categoria eliminada = Categoria.builder().nombre("Bebidas").build();
        eliminada.setEstadoRegistro(EstadoRegistro.INACTIVO);

        when(categoriaRepository.findByNombreIgnoreCase("Bebidas")).thenReturn(Optional.of(eliminada));

        assertTrue(tieneMensaje(validator.validate(dto(null, "Bebidas", null)), "eliminada"));
    }

    @Test
    @DisplayName("al editar, excluye la propia categoría de la verificación de duplicados")
    void edicionExcluyeAMismo() {
        when(categoriaRepository.findByNombreIgnoreCaseAndIdNot("Bebidas", 1L))
                .thenReturn(Optional.empty());

        assertFalse(validator.validate(dto(1L, "Bebidas", null)).hasErrors());
    }
}
