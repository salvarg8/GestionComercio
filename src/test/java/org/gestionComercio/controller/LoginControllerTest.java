package org.gestionComercio.controller;

import org.gestionComercio.entity.Permiso;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthenticationException;
import org.gestionComercio.licencia.EstadoLicencia;
import org.gestionComercio.licencia.SituacionLicencia;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.AuthenticationService;
import org.gestionComercio.service.LicenciaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginControllerTest {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private SesionUsuario sesionUsuario;

    @Mock
    private Navigator navigator;

    @Mock
    private LicenciaService licenciaService;

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
        when(licenciaService.verificarAlIniciarSesion()).thenReturn(estado(SituacionLicencia.ACTIVA));

        // Act
        loginController.performLogin(username, password);

        // Assert
        verify(authenticationService).authenticate(username, password);
        verify(sesionUsuario).iniciarSesion(usuarioValido, Set.of());
        verify(navigator).navigate(AppView.DASHBOARD);
    }

    @Test
    @DisplayName("✓ sin licencia vigente, la sesión queda sólo con los permisos de consulta")
    void loginEnModoSoloConsulta() {
        Usuario usuario = usuarioConPermisos(PermisoCodigo.VENTA_VER, PermisoCodigo.VENTA_CREAR);
        when(authenticationService.authenticate("u", "p")).thenReturn(usuario);
        when(licenciaService.verificarAlIniciarSesion()).thenReturn(estado(SituacionLicencia.PRUEBA_VENCIDA));

        loginController.performLogin("u", "p");

        verify(sesionUsuario).iniciarSesion(usuario, Set.of(PermisoCodigo.VENTA_VER));
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
        verify(sesionUsuario, never()).iniciarSesion(any(), any());
        verify(navigator, never()).navigate(any(AppView.class));
    }

    private static EstadoLicencia estado(SituacionLicencia situacion) {
        return new EstadoLicencia(situacion, null, "AAAA-BBBB-CCCC-DDDD", null, null, LocalDate.now(), null);
    }

    private static Usuario usuarioConPermisos(PermisoCodigo... codigos) {
        Rol rol = new Rol();
        rol.setPermisos(Set.of(codigos).stream()
                .map(c -> Permiso.builder().codigo(c).build())
                .collect(java.util.stream.Collectors.toSet()));
        Usuario usuario = new Usuario();
        usuario.setRol(rol);
        return usuario;
    }
}
