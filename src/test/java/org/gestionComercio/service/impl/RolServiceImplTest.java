package org.gestionComercio.service.impl;

import org.gestionComercio.dto.rol.RolDto;
import org.gestionComercio.entity.Permiso;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.RolMapper;
import org.gestionComercio.repository.PermisoRepository;
import org.gestionComercio.repository.RolRepository;
import org.gestionComercio.validation.RolValidator;
import org.gestionComercio.validation.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolServiceImplTest {

    @Mock
    private RolRepository rolRepository;

    @Mock
    private PermisoRepository permisoRepository;

    @Mock
    private RolMapper rolMapper;

    @Mock
    private RolValidator rolValidator;

    @InjectMocks
    private RolServiceImpl rolService;

    @Test
    @DisplayName("save() debería guardar un rol correctamente")
    void shouldSaveRolSuccessfully() {

        // Arrange
        RolDto dto = crearRolDto();

        Rol rol = crearRol();

        Permiso permiso = crearPermiso(1L);

        ValidationResult validationResult = new ValidationResult();

        when(rolValidator.validate(dto)).thenReturn(validationResult);
        when(rolMapper.toEntity(dto)).thenReturn(rol);
        when(permisoRepository.findById(1L)).thenReturn(Optional.of(permiso));
        when(rolRepository.save(rol)).thenReturn(rol);
        when(rolMapper.toDto(rol)).thenReturn(dto);

        // Act
        RolDto resultado = rolService.save(dto);

        // Assert
        assertNotNull(resultado);
        assertEquals(dto, resultado);

        verify(rolRepository).save(rol);
        verify(rolMapper).toEntity(dto);
        verify(rolMapper).toDto(rol);
    }

    @Test
    @DisplayName("save() debería normalizar nombre y descripción")
    void shouldNormalizeFieldsBeforeSaving() {

        // Arrange
        RolDto dto = RolDto.builder()
                .nombre("   ADMIN   ")
                .descripcion("   Administrador del sistema   ")
                .permisoIds(Set.of(1L))
                .build();

        Rol rol = crearRol();

        Permiso permiso = crearPermiso(1L);

        when(rolValidator.validate(any())).thenReturn(new ValidationResult());
        when(rolMapper.toEntity(any())).thenReturn(rol);
        when(permisoRepository.findById(1L)).thenReturn(Optional.of(permiso));
        when(rolRepository.save(any())).thenReturn(rol);
        when(rolMapper.toDto(any())).thenReturn(dto);

        // Act
        rolService.save(dto);

        // Assert
        assertEquals("ADMIN", dto.getNombre());
        assertEquals("Administrador del sistema", dto.getDescripcion());
    }

    @Test
    @DisplayName("save() debería cargar correctamente los permisos")
    void shouldLoadPermissionsWhenSaving() {

        // Arrange
        RolDto dto = crearRolDto();

        Rol rol = crearRol();

        Permiso permiso = crearPermiso(1L);

        when(rolValidator.validate(any())).thenReturn(new ValidationResult());
        when(rolMapper.toEntity(any())).thenReturn(rol);
        when(permisoRepository.findById(1L)).thenReturn(Optional.of(permiso));
        when(rolRepository.save(any())).thenReturn(rol);
        when(rolMapper.toDto(any())).thenReturn(dto);

        // Act
        rolService.save(dto);

        // Assert
        ArgumentCaptor<Rol> captor = ArgumentCaptor.forClass(Rol.class);

        verify(rolRepository).save(captor.capture());

        Rol rolGuardado = captor.getValue();

        assertEquals(1, rolGuardado.getPermisos().size());
        assertTrue(rolGuardado.getPermisos().contains(permiso));
    }

    @Test
    @DisplayName("save() debería lanzar ValidationException cuando la validación falla")
    void shouldThrowValidationExceptionWhenValidationFails() {

        // Arrange
        RolDto dto = crearRolDto();

        ValidationResult result = new ValidationResult();
        result.addError("nombre", "El nombre es obligatorio.");

        when(rolValidator.validate(dto)).thenReturn(result);

        // Act & Assert
        assertThrows(
                ValidationException.class,
                () -> rolService.save(dto)
        );

        verifyNoInteractions(permisoRepository);
        verifyNoInteractions(rolMapper);
        verify(rolRepository, never()).save(any());
    }

    @Test
    @DisplayName("save() debería lanzar EntityNotFoundException cuando un permiso no existe")
    void shouldThrowEntityNotFoundExceptionWhenPermissionDoesNotExist() {

        // Arrange
        RolDto dto = crearRolDto();

        Rol rol = crearRol();

        when(rolValidator.validate(any())).thenReturn(new ValidationResult());
        when(rolMapper.toEntity(dto)).thenReturn(rol);
        when(permisoRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> rolService.save(dto)
        );

        assertEquals(
                "No existe un permiso con ID: 1",
                exception.getMessage()
        );

        verify(rolRepository, never()).save(any());
    }

    // ==========================================================
    // Helpers
    // ==========================================================

    private RolDto crearRolDto() {

        return RolDto.builder()
                .id(1L)
                .nombre("ADMIN")
                .descripcion("Administrador")
                .permisoIds(Set.of(1L))
                .build();
    }

    private Rol crearRol() {

        Rol rol = new Rol();

        rol.setId(1L);
        rol.setNombre("ADMIN");
        rol.setDescripcion("Administrador");

        return rol;
    }

    private Permiso crearPermiso(Long id) {

        Permiso permiso = new Permiso();

        permiso.setId(id);

        return permiso;
    }

    @Test
    @DisplayName("update() debería actualizar un rol correctamente")
    void shouldUpdateRolSuccessfully() {

        // Arrange
        RolDto dto = crearRolDto();

        Rol rol = crearRol();

        Permiso permiso = crearPermiso(1L);

        when(rolValidator.validate(dto)).thenReturn(new ValidationResult());
        when(rolRepository.findById(dto.getId())).thenReturn(Optional.of(rol));
        when(permisoRepository.findById(1L)).thenReturn(Optional.of(permiso));
        when(rolRepository.save(rol)).thenReturn(rol);
        when(rolMapper.toDto(rol)).thenReturn(dto);

        // Act
        RolDto resultado = rolService.update(dto);

        // Assert
        assertNotNull(resultado);
        assertEquals(dto, resultado);

        verify(rolMapper).updateEntity(dto, rol);
        verify(rolRepository).save(rol);
    }

    @Test
    @DisplayName("update() debería actualizar los permisos")
    void shouldUpdatePermissions() {

        // Arrange
        RolDto dto = crearRolDto();

        Rol rol = crearRol();

        Permiso permiso = crearPermiso(1L);

        when(rolValidator.validate(any())).thenReturn(new ValidationResult());
        when(rolRepository.findById(dto.getId())).thenReturn(Optional.of(rol));
        when(permisoRepository.findById(1L)).thenReturn(Optional.of(permiso));
        when(rolRepository.save(any())).thenReturn(rol);
        when(rolMapper.toDto(any())).thenReturn(dto);

        // Act
        rolService.update(dto);

        // Assert
        ArgumentCaptor<Rol> captor = ArgumentCaptor.forClass(Rol.class);

        verify(rolRepository).save(captor.capture());

        Rol actualizado = captor.getValue();

        assertEquals(1, actualizado.getPermisos().size());
        assertTrue(actualizado.getPermisos().contains(permiso));
    }

    @Test
    @DisplayName("update() debería lanzar EntityNotFoundException cuando el rol no existe")
    void shouldThrowEntityNotFoundExceptionWhenRolDoesNotExist() {

        // Arrange
        RolDto dto = crearRolDto();

        when(rolValidator.validate(dto)).thenReturn(new ValidationResult());
        when(rolRepository.findById(dto.getId())).thenReturn(Optional.empty());

        // Act & Assert
        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> rolService.update(dto)
        );

        assertEquals(
                "No existe un rol con ID: " + dto.getId(),
                exception.getMessage()
        );

        verify(rolRepository, never()).save(any());
    }

    @Test
    @DisplayName("update() debería lanzar EntityNotFoundException cuando un permiso no existe")
    void shouldThrowEntityNotFoundExceptionWhenUpdatingWithInvalidPermission() {

        // Arrange
        RolDto dto = crearRolDto();

        Rol rol = crearRol();

        when(rolValidator.validate(dto)).thenReturn(new ValidationResult());
        when(rolRepository.findById(dto.getId())).thenReturn(Optional.of(rol));
        when(permisoRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(
                EntityNotFoundException.class,
                () -> rolService.update(dto)
        );

        verify(rolRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete() debería realizar una baja lógica")
    void shouldDeleteLogically() {

        // Arrange
        Rol rol = crearRol();

        when(rolRepository.findById(1L)).thenReturn(Optional.of(rol));

        // Act
        rolService.delete(1L);

        // Assert
        assertEquals(EstadoRegistro.INACTIVO, rol.getEstadoRegistro());

        verify(rolRepository).save(rol);
    }

    @Test
    @DisplayName("findById() debería devolver el rol cuando existe")
    void shouldFindRolById() {

        // Arrange
        Rol rol = crearRol();

        RolDto dto = crearRolDto();

        when(rolRepository.findById(1L)).thenReturn(Optional.of(rol));
        when(rolMapper.toDto(rol)).thenReturn(dto);

        // Act
        Optional<RolDto> resultado = rolService.findById(1L);

        // Assert
        assertTrue(resultado.isPresent());
        assertEquals(dto, resultado.get());
    }

    @Test
    @DisplayName("findById() debería devolver Optional.empty cuando no existe")
    void shouldReturnEmptyWhenRolDoesNotExist() {

        when(rolRepository.findById(1L)).thenReturn(Optional.empty());

        Optional<RolDto> resultado = rolService.findById(1L);

        assertTrue(resultado.isEmpty());
    }

    @Test
    @DisplayName("findAll() debería devolver todos los roles")
    void shouldFindAllRoles() {

        // Arrange
        Rol rol1 = crearRol();
        Rol rol2 = crearRol();
        rol2.setId(2L);

        RolDto dto1 = crearRolDto();
        RolDto dto2 = crearRolDto();
        dto2.setId(2L);

        when(rolRepository.findAll()).thenReturn(java.util.List.of(rol1, rol2));
        when(rolMapper.toDto(rol1)).thenReturn(dto1);
        when(rolMapper.toDto(rol2)).thenReturn(dto2);

        // Act
        var resultado = rolService.findAll();

        // Assert
        assertEquals(2, resultado.size());

        verify(rolMapper).toDto(rol1);
        verify(rolMapper).toDto(rol2);
    }

    @Test
    @DisplayName("search() debería buscar roles por nombre")
    void shouldSearchRoles() {

        // Arrange
        Rol rol = crearRol();

        RolDto dto = crearRolDto();

        when(rolRepository.findByNombreContainingIgnoreCase("ADM"))
                .thenReturn(java.util.List.of(rol));

        when(rolMapper.toDto(rol)).thenReturn(dto);

        // Act
        var resultado = rolService.search("ADM");

        // Assert
        assertEquals(1, resultado.size());
        assertEquals(dto, resultado.get(0));
    }

    @Test
    @DisplayName("count() debería devolver la cantidad de roles")
    void shouldCountRoles() {

        when(rolRepository.count()).thenReturn(10L);

        long cantidad = rolService.count();

        assertEquals(10L, cantidad);
    }
}