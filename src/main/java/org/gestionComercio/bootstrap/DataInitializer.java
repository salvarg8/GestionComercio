package org.gestionComercio.bootstrap;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.Permiso;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoUsuario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.PermisoRepository;
import org.gestionComercio.repository.RolRepository;
import org.gestionComercio.repository.UsuarioRepository;
import org.gestionComercio.service.PasswordService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final EmpresaRepository empresaRepository;
    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordService passwordService;
    private final PermisoRepository permisoRepository;

    @Override
    public void run(String... args) {

        Empresa empresa = crearEmpresaSiNoExiste();

        crearPermisosSiNoExisten();

        Rol rolAdmin = crearRolAdminSiNoExiste();

        crearUsuarioAdminSiNoExiste(empresa, rolAdmin);
    }

    private Empresa crearEmpresaSiNoExiste() {

        return empresaRepository.findByCuit("30000000001")
                .orElseGet(() -> {

                    Empresa empresa = Empresa.builder()
                            .razonSocial("Empresa Demo")
                            .nombreFantasia("Gestión Comercio")
                            .cuit("30000000001")
                            .direccion("Av. Siempre Viva 123")
                            .telefono("3510000000")
                            .email("admin@gestioncomercio.com")
                            .condicionIva(CondicionIVA.RESPONSABLE_INSCRIPTO)
                            .build();

                    return empresaRepository.save(empresa);
                });
    }

    private Rol crearRolAdminSiNoExiste() {

        Set<Permiso> permisos =
                new LinkedHashSet<>(permisoRepository.findAllByOrderByCodigoAsc());

        return rolRepository.findByNombre("ADMIN")
                .map(rol -> {

                    rol.setPermisos(permisos);

                    return rolRepository.save(rol);

                })
                .orElseGet(() -> {

                    Rol rol = Rol.builder()
                            .nombre("ADMIN")
                            .descripcion("Administrador del sistema")
                            .permisos(permisos)
                            .build();

                    return rolRepository.save(rol);
                });
    }

    private void crearUsuarioAdminSiNoExiste(Empresa empresa, Rol rol) {

        if (usuarioRepository.findByUsuario("admin").isPresent()) {
            return;
        }

        Usuario usuario = Usuario.builder()
                .usuario("admin")
                .password(passwordService.encode("admin123"))
                .nombre("Administrador")
                .apellido("Sistema")
                .estadoUsuario(EstadoUsuario.ACTIVO)
                .empresa(empresa)
                .rol(rol)
                .tipoDocumento(TipoDocumento.DNI)
                .documento("00000000")
                .build();

        usuarioRepository.save(usuario);
    }
    private void crearPermisosSiNoExisten() {

        for (PermisoCodigo codigo : PermisoCodigo.values()) {

            if (permisoRepository.existsByCodigo(codigo)) {
                continue;
            }

            Permiso permiso = Permiso.builder()
                    .codigo(codigo)
                    .descripcion(codigo.getDescripcion())
                    .build();

            permisoRepository.save(permiso);
        }
    }
}