package org.gestionComercio.service.impl;

import org.gestionComercio.arca.AlmacenClaveEnMemoria;
import org.gestionComercio.arca.ArcaProperties;
import org.gestionComercio.arca.CertificadosArca;
import org.gestionComercio.arca.CertificadosDePrueba;
import org.gestionComercio.arca.ClaveCertificadoArca;
import org.gestionComercio.arca.CredencialArca;
import org.gestionComercio.arca.WsfeCliente;
import org.gestionComercio.dto.arca.ConfiguracionArcaDto;
import org.gestionComercio.entity.ConfiguracionArca;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.AmbienteArca;
import org.gestionComercio.enums.OrigenClaveArca;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.repository.ComprobanteArcaRepository;
import org.gestionComercio.repository.ConfiguracionArcaRepository;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.TicketAccesoArcaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.impl.arca.ConfiguracionArcaVigente;
import org.gestionComercio.service.impl.arca.TicketAccesoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Carga de la clave del certificado en esta PC y descarte del certificado cuando se perdió la clave. */
class ConfiguracionArcaClaveTest {

    private static final String CLAVE = "clave-del-comercio";

    private final ConfiguracionArcaRepository configuracionRepository = mock(ConfiguracionArcaRepository.class);
    private final TicketAccesoArcaRepository ticketRepository = mock(TicketAccesoArcaRepository.class);
    private final AlmacenClaveEnMemoria almacen = new AlmacenClaveEnMemoria();
    private final SesionUsuario sesion = new SesionUsuario();

    private ConfiguracionArca config;

    @BeforeEach
    void setUp() {
        config = ConfiguracionArca.builder().ambiente(AmbienteArca.HOMOLOGACION).build();
        config.setId(1L);
        when(configuracionRepository.findFirstByOrderByIdAsc()).thenAnswer(i -> Optional.of(config));
        when(configuracionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        sesion.iniciarSesion(Usuario.builder().usuario("admin").build(), EnumSet.of(PermisoCodigo.FACTURACION_CONFIGURAR));
    }

    private ConfiguracionArcaServiceImpl servicio(String variableDeEntorno) {
        ClaveCertificadoArca clave = new ClaveCertificadoArca(
                new ArcaProperties(variableDeEntorno, null, null, null, null), almacen);

        return new ConfiguracionArcaServiceImpl(configuracionRepository, mock(EmpresaRepository.class),
                mock(ComprobanteArcaRepository.class), ticketRepository, mock(ConfiguracionArcaVigente.class),
                mock(TicketAccesoService.class), mock(WsfeCliente.class), clave, sesion);
    }

    private void conCertificadoCifradoCon(String clave) {
        CredencialArca credencial = CertificadosDePrueba.credencial("30712345671");
        config.setCertificado(CertificadosArca.crearPkcs12(credencial.clavePrivada(), credencial.certificado(),
                clave.toCharArray()));
        config.setCertificadoTitular("CN=pc1");
        config.setCertificadoVencimiento(LocalDateTime.now().plusYears(2));
        config.setPuntoVenta(3);
        config.setHabilitada(true);
    }

    @Test
    @DisplayName("En la primera PC (sin certificado guardado) se acepta cualquier clave de 8 caracteres o más")
    void primeraPc() {
        ConfiguracionArcaDto dto = servicio(null).guardarClaveEnEstaPc(CLAVE.toCharArray());

        assertThat(almacen.leer()).contains(CLAVE);
        assertThat(dto.isClaveConfigurada()).isTrue();
        assertThat(dto.getOrigenClave()).isEqualTo(OrigenClaveArca.ESTA_PC);
        assertThat(dto.getClaveCorrecta()).isNull();
    }

    @Test
    @DisplayName("Una clave corta se rechaza")
    void claveCorta() {
        assertThatThrownBy(() -> servicio(null).guardarClaveEnEstaPc("corta".toCharArray()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("al menos 8");
        assertThat(almacen.leer()).isEmpty();
    }

    @Test
    @DisplayName("Con un certificado guardado, sólo se acepta la clave que lo abre")
    void otraPc() {
        conCertificadoCifradoCon(CLAVE);
        ConfiguracionArcaServiceImpl servicio = servicio(null);

        assertThatThrownBy(() -> servicio.guardarClaveEnEstaPc("otra-clave-distinta".toCharArray()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no corresponde");
        assertThat(almacen.leer()).isEmpty();

        ConfiguracionArcaDto dto = servicio.guardarClaveEnEstaPc(CLAVE.toCharArray());

        assertThat(almacen.leer()).contains(CLAVE);
        assertThat(dto.getClaveCorrecta()).isTrue();
    }

    @Test
    @DisplayName("Si la clave guardada en la PC no abre el certificado, se informa como incorrecta")
    void claveGuardadaIncorrecta() {
        conCertificadoCifradoCon(CLAVE);
        almacen.guardar("otra-clave-distinta");

        ConfiguracionArcaDto dto = servicio(null).obtener();

        assertThat(dto.isClaveConfigurada()).isTrue();
        assertThat(dto.getClaveCorrecta()).isFalse();
    }

    @Test
    @DisplayName("La variable de entorno tiene prioridad y no se reemplaza desde la pantalla")
    void variableDeEntorno() {
        ConfiguracionArcaServiceImpl servicio = servicio("clave-de-la-variable");

        assertThat(servicio.obtener().getOrigenClave()).isEqualTo(OrigenClaveArca.VARIABLE_ENTORNO);
        assertThatThrownBy(() -> servicio.guardarClaveEnEstaPc(CLAVE.toCharArray()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ARCA_CLAVE_CERTIFICADO");
    }

    @Test
    @DisplayName("Cambiar la clave vuelve a cifrar el certificado y el pedido, y guarda la nueva en esta PC")
    void cambiarClave() {
        conCertificadoCifradoCon(CLAVE);
        CredencialArca pedido = CertificadosDePrueba.credencial("30712345671");
        config.setPedidoCertificado(CertificadosArca.crearPkcs12(pedido.clavePrivada(), pedido.certificado(),
                CLAVE.toCharArray()));
        byte[] certificadoAnterior = config.getCertificado();
        almacen.guardar(CLAVE);

        ConfiguracionArcaDto dto = servicio(null).cambiarClave("clave-nueva-del-comercio".toCharArray());

        assertThat(almacen.leer()).contains("clave-nueva-del-comercio");
        assertThat(dto.getClaveCorrecta()).isTrue();
        assertThat(dto.isHabilitada()).isTrue();

        // El mismo certificado, ahora cifrado con la clave nueva.
        CredencialArca recifrado = CertificadosArca.leerPkcs12(config.getCertificado(),
                "clave-nueva-del-comercio".toCharArray());
        assertThat(recifrado.certificado())
                .isEqualTo(CertificadosArca.leerPkcs12(certificadoAnterior, CLAVE.toCharArray()).certificado());
        assertThatThrownBy(() -> CertificadosArca.leerPkcs12(config.getCertificado(), CLAVE.toCharArray()))
                .isInstanceOf(org.gestionComercio.arca.ArcaException.class);
        assertThat(CertificadosArca.leerPkcs12(config.getPedidoCertificado(),
                "clave-nueva-del-comercio".toCharArray()).certificado()).isEqualTo(pedido.certificado());
    }

    @Test
    @DisplayName("No se cambia la clave sin la actual, con una que no abre el certificado o con la misma")
    void cambiarClaveRechazos() {
        conCertificadoCifradoCon(CLAVE);
        byte[] certificado = config.getCertificado();
        ConfiguracionArcaServiceImpl servicio = servicio(null);

        assertThatThrownBy(() -> servicio.cambiarClave("clave-nueva-del-comercio".toCharArray()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no tiene la clave actual");

        almacen.guardar("otra-clave-distinta");
        assertThatThrownBy(() -> servicio.cambiarClave("clave-nueva-del-comercio".toCharArray()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no abre el certificado");
        assertThat(config.getCertificado()).isSameAs(certificado);
        assertThat(almacen.leer()).contains("otra-clave-distinta");

        almacen.guardar(CLAVE);
        assertThatThrownBy(() -> servicio.cambiarClave(CLAVE.toCharArray()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("igual a la actual");
        assertThatThrownBy(() -> servicio.cambiarClave("corta".toCharArray()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("al menos 8");

        assertThatThrownBy(() -> servicio("clave-de-la-variable").cambiarClave("clave-nueva-del-comercio".toCharArray()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ARCA_CLAVE_CERTIFICADO");
    }

    @Test
    @DisplayName("Descartar el certificado lo quita, deshabilita la facturación y borra los tickets de acceso")
    void descartarCertificado() {
        conCertificadoCifradoCon(CLAVE);

        ConfiguracionArcaDto dto = servicio(null).descartarCertificado();

        assertThat(dto.isCertificadoCargado()).isFalse();
        assertThat(dto.isHabilitada()).isFalse();
        assertThat(config.getCertificado()).isNull();
        assertThat(config.getCertificadoVencimiento()).isNull();
        assertThat(config.getPuntoVenta()).isEqualTo(3);
        verify(ticketRepository).borrarTodos();
    }

    @Test
    @DisplayName("Cargar la clave o descartar el certificado requiere FACTURACION_CONFIGURAR")
    void requierePermiso() {
        sesion.iniciarSesion(Usuario.builder().usuario("admin").build(), EnumSet.of(PermisoCodigo.VENTA_CREAR));
        ConfiguracionArcaServiceImpl servicio = servicio(null);

        assertThatThrownBy(() -> servicio.guardarClaveEnEstaPc(CLAVE.toCharArray()))
                .isInstanceOf(AuthorizationException.class);
        assertThatThrownBy(servicio::descartarCertificado).isInstanceOf(AuthorizationException.class);
    }

}
