package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.rol.RolDto;
import org.gestionComercio.dto.usuario.UsuarioDto;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.EstadoUsuario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.RolMapper;
import org.gestionComercio.mapper.UsuarioMapper;
import org.gestionComercio.repository.RolRepository;
import org.gestionComercio.repository.UsuarioRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.PasswordService;
import org.gestionComercio.service.UsuarioService;
import org.gestionComercio.validation.PasswordPolicy;
import org.gestionComercio.validation.UsuarioValidator;
import org.gestionComercio.validation.ValidationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;

    private final UsuarioMapper usuarioMapper;
    private final RolMapper rolMapper;
    private final UsuarioValidator usuarioValidator;
    private final PasswordService passwordService;
    private final SesionUsuario sesionUsuario;

    @Override
    public UsuarioDto save(UsuarioDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.USUARIO_CREAR);

        normalizar(dto);
        validar(dto);

        Usuario usuario = usuarioMapper.toEntity(dto);

        usuario.setId(null);
        usuario.setPassword(passwordService.encode(dto.getPassword()));
        usuario.setRol(buscarRolActivo(dto.getRolId()));
        usuario.setEmpresa(buscarUsuario(idUsuarioActual()).getEmpresa());

        return usuarioMapper.toDto(usuarioRepository.save(usuario));
    }

    @Override
    public UsuarioDto update(UsuarioDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.USUARIO_EDITAR);

        normalizar(dto);
        validar(dto);

        Usuario usuario = buscarUsuario(dto.getId());

        if (esUsuarioActual(usuario.getId())) {
            validarCambiosSobreUnoMismo(usuario, dto);
        }

        usuarioMapper.updateEntity(dto, usuario);
        usuario.setRol(buscarRolActivo(dto.getRolId()));

        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            usuario.setPassword(passwordService.encode(dto.getPassword()));
        }

        return usuarioMapper.toDto(usuarioRepository.save(usuario));
    }

    @Override
    public void delete(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.USUARIO_ELIMINAR);

        if (esUsuarioActual(id)) {
            throw new BusinessException("No puede eliminar su propio usuario.");
        }

        Usuario usuario = buscarUsuario(id);

        usuario.setEstadoRegistro(EstadoRegistro.INACTIVO);
        usuario.setEstadoUsuario(EstadoUsuario.INACTIVO);

        usuarioRepository.save(usuario);
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<UsuarioDto> findById(Long id) {

        sesionUsuario.requerirPermiso(PermisoCodigo.USUARIO_VER);

        return usuarioRepository.findById(id)
                .map(usuarioMapper::toDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<UsuarioDto> findAll() {

        sesionUsuario.requerirPermiso(PermisoCodigo.USUARIO_VER);

        return usuarioRepository
                .findByEstadoRegistroOrderByApellidoAscNombreAsc(EstadoRegistro.ACTIVO)
                .stream()
                .map(usuarioMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<UsuarioDto> search(String texto) {

        if (texto == null || texto.isBlank()) {
            return findAll();
        }

        sesionUsuario.requerirPermiso(PermisoCodigo.USUARIO_VER);

        return usuarioRepository.buscar(texto.trim(), EstadoRegistro.ACTIVO)
                .stream()
                .map(usuarioMapper::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public long count() {

        sesionUsuario.requerirPermiso(PermisoCodigo.USUARIO_VER);

        return usuarioRepository.count();
    }

    @Transactional(readOnly = true)
    @Override
    public List<RolDto> findRolesDisponibles() {

        sesionUsuario.requerirAlgunPermiso(
                PermisoCodigo.USUARIO_CREAR,
                PermisoCodigo.USUARIO_EDITAR);

        return rolRepository.findAll()
                .stream()
                .filter(rol -> rol.getEstadoRegistro() == EstadoRegistro.ACTIVO)
                .map(rolMapper::toDto)
                .toList();
    }

    @Override
    public void cambiarPassword(String passwordActual, String passwordNueva) {

        if (!sesionUsuario.estaAutenticado()) {
            throw new AuthorizationException("Debe iniciar sesión para cambiar la contraseña.");
        }

        Usuario usuario = buscarUsuario(idUsuarioActual());

        ValidationResult result = new ValidationResult();

        if (passwordActual == null || passwordActual.isEmpty()) {
            result.addError("passwordActual", "Debe ingresar la contraseña actual.");
        } else if (!passwordService.matches(passwordActual, usuario.getPassword())) {
            result.addError("passwordActual", "La contraseña actual es incorrecta.");
        }

        PasswordPolicy.validar(passwordNueva, "passwordNueva", result);

        if (passwordNueva != null && passwordNueva.equals(passwordActual)) {
            result.addError("passwordNueva",
                    "La nueva contraseña debe ser distinta de la actual.");
        }

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }

        usuario.setPassword(passwordService.encode(passwordNueva));

        usuarioRepository.save(usuario);
    }

    /**
     * Evita que un usuario se bloquee a sí mismo: no puede cambiar su propio
     * estado ni su propio rol (perdería el acceso a la pantalla para revertirlo).
     */
    private void validarCambiosSobreUnoMismo(Usuario usuario, UsuarioDto dto) {

        if (dto.getEstadoUsuario() != null
                && dto.getEstadoUsuario() != EstadoUsuario.ACTIVO) {
            throw new BusinessException("No puede desactivar ni bloquear su propio usuario.");
        }

        if (!Objects.equals(usuario.getRol().getId(), dto.getRolId())) {
            throw new BusinessException("No puede cambiar su propio rol.");
        }
    }

    private void validar(UsuarioDto dto) {

        ValidationResult result = usuarioValidator.validate(dto);

        if (result.hasErrors()) {
            throw new ValidationException(result);
        }
    }

    private void normalizar(UsuarioDto dto) {

        if (dto.getUsuario() != null) {
            dto.setUsuario(dto.getUsuario().trim());
        }

        if (dto.getNombre() != null) {
            dto.setNombre(dto.getNombre().trim());
        }

        if (dto.getApellido() != null) {
            dto.setApellido(dto.getApellido().trim());
        }

        if (dto.getDocumento() != null) {
            dto.setDocumento(dto.getDocumento().trim());
        }
    }

    private boolean esUsuarioActual(Long id) {
        return sesionUsuario.estaAutenticado()
                && Objects.equals(sesionUsuario.getUsuario().getId(), id);
    }

    private Long idUsuarioActual() {
        return sesionUsuario.getUsuario().getId();
    }

    private Usuario buscarUsuario(Long id) {

        return usuarioRepository.findById(id)
                .orElseThrow(() ->
                        new EntityNotFoundException("No existe un usuario con ID: " + id));
    }

    private Rol buscarRolActivo(Long id) {

        return rolRepository.findById(id)
                .filter(rol -> rol.getEstadoRegistro() == EstadoRegistro.ACTIVO)
                .orElseThrow(() ->
                        new EntityNotFoundException("No existe un rol activo con ID: " + id));
    }
}
