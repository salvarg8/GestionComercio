package org.gestionComercio.service.impl;

import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoUsuario;
import org.gestionComercio.exception.AuthenticationException;
import org.gestionComercio.repository.UsuarioRepository;
import org.gestionComercio.service.PasswordService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    @Test
    @DisplayName("✓ usuario inexistente")
    void testAuthenticate_UsuarioInexistente() {
        // Arrange
        String username = "inexistente";
        String password = "password";
        when(usuarioRepository.findByUsuario(username)).thenReturn(Optional.empty());

        // Act & Assert
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authenticationService.authenticate(username, password);
        });
        assertEquals("Usuario o contraseña incorrectos.", exception.getMessage());
    }

    @Test
    @DisplayName("✓ contraseña incorrecta")
    void testAuthenticate_ContraseñaIncorrecta() {
        // Arrange
        String username = "usuario";
        String password = "wrongpassword";
        String encodedPassword = "encodedPassword";

        Usuario usuario = new Usuario();
        usuario.setUsuario(username);
        usuario.setPassword(encodedPassword);
        usuario.setEstadoUsuario(EstadoUsuario.ACTIVO);

        when(usuarioRepository.findByUsuario(username)).thenReturn(Optional.of(usuario));
        when(passwordService.matches(password, encodedPassword)).thenReturn(false);

        // Act & Assert
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authenticationService.authenticate(username, password);
        });
        assertEquals("Usuario o contraseña incorrectos.", exception.getMessage());
    }

    @Test
    @DisplayName("✓ usuario inactivo")
    void testAuthenticate_UsuarioInactivo() {
        // Arrange
        String username = "inactivo";
        String password = "password";

        Usuario usuario = new Usuario();
        usuario.setUsuario(username);
        usuario.setEstadoUsuario(EstadoUsuario.INACTIVO); // Estado inactivo

        when(usuarioRepository.findByUsuario(username)).thenReturn(Optional.of(usuario));

        // Act & Assert
        AuthenticationException exception = assertThrows(AuthenticationException.class, () -> {
            authenticationService.authenticate(username, password);
        });
        assertEquals("El usuario no está activo.", exception.getMessage());
    }

    @Test
    @DisplayName("✓ usuario activo y contraseña correcta")
    void testAuthenticate_UsuarioActivoYContraseñaCorrecta() {
        // Arrange
        String username = "usuario";
        String password = "password";
        String encodedPassword = "encodedPassword";

        Usuario usuario = new Usuario();
        usuario.setUsuario(username);
        usuario.setPassword(encodedPassword);
        usuario.setEstadoUsuario(EstadoUsuario.ACTIVO);

        when(usuarioRepository.findByUsuario(username)).thenReturn(Optional.of(usuario));
        when(passwordService.matches(password, encodedPassword)).thenReturn(true);

        // Act
        Usuario result = authenticationService.authenticate(username, password);

        // Assert
        assertNotNull(result);
        assertEquals(username, result.getUsuario());
    }
}
