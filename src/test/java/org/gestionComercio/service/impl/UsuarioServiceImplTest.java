package org.gestionComercio.service.impl;

import org.gestionComercio.dto.usuario.UsuarioDto;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.EstadoUsuario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.RolMapper;
import org.gestionComercio.mapper.UsuarioMapper;
import org.gestionComercio.repository.RolRepository;
import org.gestionComercio.repository.UsuarioRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.PasswordService;
import org.gestionComercio.validation.UsuarioValidator;
import org.gestionComercio.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    private static final Long ID_ACTUAL = 1L;
    private static final Long ID_OTRO = 2L;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private UsuarioMapper usuarioMapper;

    @Mock
    private RolMapper rolMapper;

    @Mock
    private UsuarioValidator usuarioValidator;

    @Mock
    private PasswordService passwordService;

    private SesionUsuario sesionUsuario;

    private UsuarioServiceImpl usuarioService;

    private Usuario usuarioActual;

    private Rol rolAdmin;

    @BeforeEach
    void setUp() {
        rolAdmin = Rol.builder().nombre("ADMIN").build();
        rolAdmin.setId(10L);

        usuarioActual = Usuario.builder()
                .usuario("admin")
                .password("HASH_ACTUAL")
                .rol(rolAdmin)
                .empresa(Empresa.builder().build())
                .build();
        usuarioActual.setId(ID_ACTUAL);

        sesionUsuario = new SesionUsuario();
        sesionUsuario.iniciarSesion(usuarioActual, EnumSet.allOf(PermisoCodigo.class));

        usuarioService = new UsuarioServiceImpl(
                usuarioRepository, rolRepository, usuarioMapper, rolMapper,
                usuarioValidator, passwordService, sesionUsuario);

        lenient().when(usuarioValidator.validate(any())).thenReturn(new ValidationResult());
    }

    private UsuarioDto dto(Long id) {
        return UsuarioDto.builder()
                .id(id)
                .usuario(" jperez ")
                .nombre("Juan")
                .apellido("Pérez")
                .tipoDocumento(TipoDocumento.DNI)
                .documento("30111222")
                .estadoUsuario(EstadoUsuario.ACTIVO)
                .rolId(rolAdmin.getId())
                .build();
    }

    // ------------------------------------------------------------------
    // Permisos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sin USUARIO_CREAR no se puede crear un usuario")
    void saveSinPermiso() {
        sesionUsuario.iniciarSesion(usuarioActual, EnumSet.of(PermisoCodigo.USUARIO_VER));

        assertThrows(AuthorizationException.class, () -> usuarioService.save(dto(null)));

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("sin USUARIO_VER no se puede listar")
    void findAllSinPermiso() {
        sesionUsuario.iniciarSesion(usuarioActual, EnumSet.of(PermisoCodigo.ROL_VER));

        assertThrows(AuthorizationException.class, () -> usuarioService.findAll());
    }

    // ------------------------------------------------------------------
    // Alta y edición
    // ------------------------------------------------------------------

    @Test
    @DisplayName("save() encripta la contraseña, normaliza y asigna rol y empresa del usuario actual")
    void saveEncriptaYAsigna() {
        UsuarioDto dto = dto(null);
        dto.setPassword("secreta1");

        Usuario nuevo = new Usuario();

        when(usuarioMapper.toEntity(dto)).thenReturn(nuevo);
        when(passwordService.encode("secreta1")).thenReturn("HASH_NUEVO");
        when(rolRepository.findById(rolAdmin.getId())).thenReturn(Optional.of(rolAdmin));
        when(usuarioRepository.findById(ID_ACTUAL)).thenReturn(Optional.of(usuarioActual));
        when(usuarioRepository.save(nuevo)).thenReturn(nuevo);

        usuarioService.save(dto);

        assertEquals("jperez", dto.getUsuario());
        assertEquals("HASH_NUEVO", nuevo.getPassword());
        assertEquals(rolAdmin, nuevo.getRol());
        assertEquals(usuarioActual.getEmpresa(), nuevo.getEmpresa());
    }

    @Test
    @DisplayName("update() sin contraseña conserva la actual")
    void updateSinPasswordConservaHash() {
        Usuario otro = Usuario.builder().password("HASH_OTRO").rol(rolAdmin).build();
        otro.setId(ID_OTRO);

        when(usuarioRepository.findById(ID_OTRO)).thenReturn(Optional.of(otro));
        when(rolRepository.findById(rolAdmin.getId())).thenReturn(Optional.of(rolAdmin));
        when(usuarioRepository.save(otro)).thenReturn(otro);

        usuarioService.update(dto(ID_OTRO));

        assertEquals("HASH_OTRO", otro.getPassword());
        verify(passwordService, never()).encode(anyString());
    }

    @Test
    @DisplayName("update() rechaza un rol inactivo")
    void updateRolInactivo() {
        Usuario otro = Usuario.builder().rol(rolAdmin).build();
        otro.setId(ID_OTRO);

        Rol inactivo = Rol.builder().nombre("VIEJO").build();
        inactivo.setId(99L);
        inactivo.setEstadoRegistro(EstadoRegistro.INACTIVO);

        UsuarioDto dto = dto(ID_OTRO);
        dto.setRolId(99L);

        when(usuarioRepository.findById(ID_OTRO)).thenReturn(Optional.of(otro));
        when(rolRepository.findById(99L)).thenReturn(Optional.of(inactivo));

        assertThrows(BusinessException.class, () -> usuarioService.update(dto));
        verify(usuarioRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Protecciones sobre el propio usuario
    // ------------------------------------------------------------------

    @Test
    @DisplayName("no se puede eliminar el propio usuario")
    void noEliminarseASiMismo() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> usuarioService.delete(ID_ACTUAL));

        assertTrue(ex.getMessage().contains("propio"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("no se puede bloquear el propio usuario")
    void noBloquearseASiMismo() {
        UsuarioDto dto = dto(ID_ACTUAL);
        dto.setEstadoUsuario(EstadoUsuario.BLOQUEADO);

        when(usuarioRepository.findById(ID_ACTUAL)).thenReturn(Optional.of(usuarioActual));

        assertThrows(BusinessException.class, () -> usuarioService.update(dto));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("no se puede cambiar el propio rol")
    void noCambiarPropioRol() {
        UsuarioDto dto = dto(ID_ACTUAL);
        dto.setRolId(55L);

        when(usuarioRepository.findById(ID_ACTUAL)).thenReturn(Optional.of(usuarioActual));

        assertThrows(BusinessException.class, () -> usuarioService.update(dto));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete() de otro usuario lo desactiva lógicamente")
    void deleteLogico() {
        Usuario otro = new Usuario();
        otro.setId(ID_OTRO);

        when(usuarioRepository.findById(ID_OTRO)).thenReturn(Optional.of(otro));

        usuarioService.delete(ID_OTRO);

        assertEquals(EstadoRegistro.INACTIVO, otro.getEstadoRegistro());
        assertEquals(EstadoUsuario.INACTIVO, otro.getEstadoUsuario());
        verify(usuarioRepository).save(otro);
    }

    // ------------------------------------------------------------------
    // Cambio de contraseña
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cambiarPassword() requiere sesión")
    void cambiarPasswordSinSesion() {
        sesionUsuario.cerrarSesion();

        assertThrows(AuthorizationException.class,
                () -> usuarioService.cambiarPassword("x", "nueva123"));
    }

    @Test
    @DisplayName("cambiarPassword() no requiere permisos de usuarios")
    void cambiarPasswordSinPermisos() {
        sesionUsuario.iniciarSesion(usuarioActual, EnumSet.noneOf(PermisoCodigo.class));

        when(usuarioRepository.findById(ID_ACTUAL)).thenReturn(Optional.of(usuarioActual));
        when(passwordService.matches("actual1", "HASH_ACTUAL")).thenReturn(true);
        when(passwordService.encode("nueva123")).thenReturn("HASH_NUEVO");

        usuarioService.cambiarPassword("actual1", "nueva123");

        assertEquals("HASH_NUEVO", usuarioActual.getPassword());
        verify(usuarioRepository).save(usuarioActual);
    }

    @Test
    @DisplayName("cambiarPassword() rechaza una contraseña actual incorrecta")
    void cambiarPasswordActualIncorrecta() {
        when(usuarioRepository.findById(ID_ACTUAL)).thenReturn(Optional.of(usuarioActual));
        when(passwordService.matches("mala", "HASH_ACTUAL")).thenReturn(false);

        assertThrows(ValidationException.class,
                () -> usuarioService.cambiarPassword("mala", "nueva123"));

        assertEquals("HASH_ACTUAL", usuarioActual.getPassword());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("cambiarPassword() rechaza una contraseña nueva corta o igual a la actual")
    void cambiarPasswordNuevaInvalida() {
        when(usuarioRepository.findById(ID_ACTUAL)).thenReturn(Optional.of(usuarioActual));
        when(passwordService.matches(anyString(), any())).thenReturn(true);

        assertThrows(ValidationException.class,
                () -> usuarioService.cambiarPassword("actual1", "123"));

        assertThrows(ValidationException.class,
                () -> usuarioService.cambiarPassword("actual1", "actual1"));

        verify(usuarioRepository, never()).save(any());
    }
}
