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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final EmpresaRepository empresaRepository;
    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordService passwordService;
    private final PermisoRepository permisoRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ProveedorTablaMigracion proveedorTablaMigracion;

    @Override
    public void run(String... args) {

        proveedorTablaMigracion.migrarSiCorresponde();

        Empresa empresa = crearEmpresaSiNoExiste();

        eliminarPermisosObsoletos();

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

        return rolRepository.findByNombreIgnoreCase("ADMIN")
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
    /**
     * Borra los permisos cuyo código ya no existe en PermisoCodigo (por ejemplo,
     * uno renombrado). Si quedaran en la base, Hibernate no podría leerlos y la
     * aplicación no arrancaría. Se hace por SQL porque cargarlos como entidad
     * es justamente lo que falla.
     */
    private void eliminarPermisosObsoletos() {

        String vigentes = Arrays.stream(PermisoCodigo.values())
                .map(codigo -> "'" + codigo.name() + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.update("delete from roles_permisos where permiso_id in "
                + "(select id from permisos where codigo not in (" + vigentes + "))");

        jdbcTemplate.update("delete from permisos where codigo not in (" + vigentes + ")");
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