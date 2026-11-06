package org.gestionComercio.service;

import java.util.EnumSet;
import java.util.Set;

import org.gestionComercio.entity.Permiso;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.repository.UsuarioRepository;
import org.gestionComercio.security.PermisoCodigo;
import org.gestionComercio.security.SesionUsuario;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticacionService {
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SesionUsuario sesionUsuario;

    public AutenticacionService(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            SesionUsuario sesionUsuario) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.sesionUsuario = sesionUsuario;
    }

    @Transactional(readOnly = true)
    public boolean login(String nombreUsuario, String clave) {
        sesionUsuario.cerrarSesion();

        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElse(null);
        if (usuario == null || !passwordEncoder.matches(clave, usuario.getClave())) {
            return false;
        }

        Set<PermisoCodigo> permisos = EnumSet.noneOf(PermisoCodigo.class);
        if (usuario.getRol() != null) {
            for (Permiso permiso : usuario.getRol().getPermisos()) {
                if (permiso.getCodigo() != null) {
                    permisos.add(permiso.getCodigo());
                }
            }
        }

        sesionUsuario.iniciarSesion(usuario, permisos);
        return true;
    }
}
