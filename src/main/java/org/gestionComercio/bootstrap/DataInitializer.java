package org.gestionComercio.bootstrap;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.Rol;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.EstadoUsuario;
import org.gestionComercio.enums.TipoDocumento;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.RolRepository;
import org.gestionComercio.repository.UsuarioRepository;
import org.gestionComercio.service.PasswordService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final EmpresaRepository empresaRepository;
    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordService passwordService;

    @Override
    public void run(String... args) {

        Empresa empresa = crearEmpresaSiNoExiste();
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

        return rolRepository.findByNombre("ADMIN")
                .orElseGet(() -> {

                    Rol rol = Rol.builder()
                            .nombre("ADMIN")
                            .descripcion("Administrador del sistema")
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
}