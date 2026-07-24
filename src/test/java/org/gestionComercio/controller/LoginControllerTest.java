package org.gestionComercio.controller;

import org.gestionComercio.entity.Usuario;
import org.gestionComercio.exception.AuthenticationException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.AuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private SesionUsuario sesionUsuario;

    @Mock
    private Navigator navigator;

    @InjectMocks
    private LoginController loginController;

    @Test
    @DisplayName("✓ debería autenticar correctamente y navegar al dashboard")
    void testLoginExitoso() throws AuthenticationException {
        // Arrange
        String username = "usuario";
        String password = "password";
        Usuario usuarioValido = new Usuario();
        when(authenticationService.authenticate(username, password)).thenReturn(usuarioValido);

        // Act
        loginController.performLogin(username, password);

        // Assert
        verify(authenticationService).authenticate(username, password);
        verify(sesionUsuario).setUsuario(usuarioValido);
        verify(navigator).navigate(AppView.DASHBOARD);
    }

    @Test
    @DisplayName("✓ debería lanzar excepción en login fallido y no navegar")
    void testLoginFallido() {
        // Arrange
        String username = "usuario";
        String password = "wrongpassword";
        when(authenticationService.authenticate(username, password))
                .thenThrow(new AuthenticationException("Credenciales incorrectas"));

        // Act & Assert
        assertThrows(AuthenticationException.class, () -> {
            loginController.performLogin(username, password);
        });

        // Verificamos que no hubo interacciones de éxito
        verify(sesionUsuario, never()).setUsuario(any());
        verify(navigator, never()).navigate(any(AppView.class));
    }
}
