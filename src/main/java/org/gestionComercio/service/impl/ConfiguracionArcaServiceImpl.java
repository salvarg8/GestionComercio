package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.arca.ArcaComunicacionException;
import org.gestionComercio.arca.ArcaException;
import org.gestionComercio.arca.CertificadosArca;
import org.gestionComercio.arca.ClaveCertificadoArca;
import org.gestionComercio.arca.CredencialArca;
import org.gestionComercio.arca.Wsfe;
import org.gestionComercio.arca.WsfeCliente;
import org.gestionComercio.dto.arca.ConfiguracionArcaDto;
import org.gestionComercio.dto.arca.PruebaConexionArca;
import org.gestionComercio.entity.ConfiguracionArca;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.enums.AmbienteArca;
import org.gestionComercio.enums.EstadoComprobanteArca;
import org.gestionComercio.enums.OrigenClaveArca;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoComprobante;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.repository.ComprobanteArcaRepository;
import org.gestionComercio.repository.ConfiguracionArcaRepository;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.repository.TicketAccesoArcaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ConfiguracionArcaService;
import org.gestionComercio.service.impl.arca.ConfiguracionArcaVigente;
import org.gestionComercio.service.impl.arca.ContextoArca;
import org.gestionComercio.service.impl.arca.TicketAccesoService;
import org.gestionComercio.util.CuitUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class ConfiguracionArcaServiceImpl implements ConfiguracionArcaService {

    static final int PUNTO_VENTA_MAXIMO = 99998;

    static final String PASO_CONFIGURACION = "Configuración";
    static final String PASO_SERVIDORES = "Conexión con ARCA";
    static final String PASO_CERTIFICADO = "Certificado";
    static final String PASO_PUNTO_VENTA = "Punto de venta";

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ConfiguracionArcaRepository configuracionRepository;
    private final EmpresaRepository empresaRepository;
    private final ComprobanteArcaRepository comprobanteRepository;
    private final TicketAccesoArcaRepository ticketRepository;
    private final ConfiguracionArcaVigente configuracionVigente;
    private final TicketAccesoService tickets;
    private final WsfeCliente wsfe;
    private final ClaveCertificadoArca claveCertificado;
    private final SesionUsuario sesionUsuario;

    @Transactional(readOnly = true)
    @Override
    public ConfiguracionArcaDto obtener() {

        sesionUsuario.requerirPermiso(PermisoCodigo.FACTURACION_CONFIGURAR);

        return toDto(configuracionRepository.findFirstByOrderByIdAsc().orElseGet(() -> ConfiguracionArca.builder().build()));
    }

    @Override
    public ConfiguracionArcaDto guardar(ConfiguracionArcaDto dto) {

        sesionUsuario.requerirPermiso(PermisoCodigo.FACTURACION_CONFIGURAR);

        if (dto == null) {
            throw new BusinessException("Debe indicar la configuración.");
        }

        ConfiguracionArca config = actual();
        AmbienteArca ambiente = dto.getAmbiente() == null ? AmbienteArca.HOMOLOGACION : dto.getAmbiente();
        Integer puntoVenta = dto.getPuntoVenta();

        List<String> errores = new ArrayList<>();

        if (puntoVenta != null && (puntoVenta < 1 || puntoVenta > PUNTO_VENTA_MAXIMO)) {
            errores.add("El punto de venta debe estar entre 1 y " + PUNTO_VENTA_MAXIMO + ".");
        }

        if (dto.isHabilitada()) {
            errores.addAll(faltantesParaHabilitar(config, puntoVenta));
        }

        boolean cambiaNumeracion = config.getId() != null
                && (config.getAmbiente() != ambiente || !Objects.equals(config.getPuntoVenta(), puntoVenta));

        if (cambiaNumeracion && comprobanteRepository.existsByEstado(EstadoComprobanteArca.ENVIADO)) {
            errores.add("Hay comprobantes enviados a ARCA sin confirmar. Reintente su autorización antes de "
                    + "cambiar el ambiente o el punto de venta.");
        }

        if (!errores.isEmpty()) {
            throw new BusinessException(String.join("\n", errores));
        }

        config.setAmbiente(ambiente);
        config.setPuntoVenta(puntoVenta);
        config.setHabilitada(dto.isHabilitada());

        return toDto(configuracionRepository.save(config));
    }

    @Override
    public ConfiguracionArcaDto guardarClaveEnEstaPc(char[] clave) {

        sesionUsuario.requerirPermiso(PermisoCodigo.FACTURACION_CONFIGURAR);

        noUsaVariableDeEntorno();
        validarClaveNueva(clave);

        ConfiguracionArca config = actual();
        byte[] guardado = guardado(config);

        if (guardado != null && !abre(guardado, clave)) {
            throw new BusinessException("La clave no corresponde al certificado guardado en el sistema. Escriba la "
                    + "misma clave que se usó en la PC donde se configuró la facturación electrónica.");
        }

        claveCertificado.guardarEnEstaPc(clave);

        return toDto(config);
    }

    @Override
    public ConfiguracionArcaDto cambiarClave(char[] nueva) {

        sesionUsuario.requerirPermiso(PermisoCodigo.FACTURACION_CONFIGURAR);

        noUsaVariableDeEntorno();

        char[] actual = claveCertificado.obtener().orElseThrow(() -> new BusinessException(
                "Esta PC no tiene la clave actual. Cárguela primero; después se puede cambiar."));

        validarClaveNueva(nueva);

        if (Arrays.equals(actual, nueva)) {
            throw new BusinessException("La clave nueva es igual a la actual.");
        }

        ConfiguracionArca config = actual();

        config.setCertificado(recifrar(config.getCertificado(), actual, nueva));
        config.setPedidoCertificado(recifrar(config.getPedidoCertificado(), actual, nueva));

        ConfiguracionArca guardada = configuracionRepository.save(config);

        // La clave nueva se guarda en esta PC recién cuando el certificado recifrado quedó en la base:
        // si la base fallara, esta PC seguiría con la clave que abre el certificado.
        char[] copia = nueva.clone();
        Runnable guardarEnEstaPc = () -> claveCertificado.guardarEnEstaPc(copia);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    guardarEnEstaPc.run();
                }
            });
        } else {
            guardarEnEstaPc.run();
        }

        // toDto todavía ve la clave anterior en esta PC: se informa como ya quedará.
        ConfiguracionArcaDto dto = toDto(guardada);
        dto.setOrigenClave(OrigenClaveArca.ESTA_PC);
        dto.setClaveConfigurada(true);
        dto.setClaveCorrecta(guardado(guardada) == null ? null : true);
        return dto;
    }

    /** Abre el PKCS#12 con la clave actual y lo vuelve a cifrar con la nueva; null si no hay nada guardado. */
    private static byte[] recifrar(byte[] pkcs12, char[] actual, char[] nueva) {

        if (pkcs12 == null) {
            return null;
        }

        CredencialArca credencial;

        try {
            credencial = CertificadosArca.leerPkcs12(pkcs12, actual);
        } catch (ArcaException e) {
            throw new BusinessException("La clave de esta PC no abre el certificado guardado. Cargue la clave "
                    + "correcta antes de cambiarla.", e);
        }

        return CertificadosArca.crearPkcs12(credencial.clavePrivada(), credencial.certificado(), nueva);
    }

    private void noUsaVariableDeEntorno() {
        if (claveCertificado.origen() == OrigenClaveArca.VARIABLE_ENTORNO) {
            throw new BusinessException("Esta PC toma la clave de la variable de entorno ARCA_CLAVE_CERTIFICADO. "
                    + "Para cambiarla, cambie esa variable.");
        }
    }

    private static void validarClaveNueva(char[] clave) {

        if (clave == null || clave.length < ClaveCertificadoArca.LARGO_MINIMO) {
            throw new BusinessException("La clave debe tener al menos " + ClaveCertificadoArca.LARGO_MINIMO
                    + " caracteres.");
        }

        if (new String(clave).isBlank()) {
            throw new BusinessException("La clave no puede ser sólo espacios.");
        }
    }

    @Override
    public ConfiguracionArcaDto descartarCertificado() {

        sesionUsuario.requerirPermiso(PermisoCodigo.FACTURACION_CONFIGURAR);

        ConfiguracionArca config = actual();

        if (config.getId() == null) {
            return toDto(config);
        }

        config.setCertificado(null);
        config.setCertificadoTitular(null);
        config.setCertificadoVencimiento(null);
        config.setPedidoCertificado(null);
        config.setPedidoFecha(null);
        config.setHabilitada(false);

        ticketRepository.borrarTodos();

        return toDto(configuracionRepository.save(config));
    }

    @Override
    public String generarPedidoCertificado() {

        sesionUsuario.requerirPermiso(PermisoCodigo.FACTURACION_CONFIGURAR);

        char[] clave = configuracionVigente.clave();
        Empresa empresa = empresa();

        CertificadosArca.PedidoCertificado pedido = CertificadosArca.generarPedido(
                CuitUtils.normalizar(empresa.getCuit()), empresa.getRazonSocial(), ALIAS_COMPUTADOR);

        // La clave del pedido se guarda con un certificado provisorio hasta importar el de ARCA.
        X509Certificate provisorio = CertificadosArca.autofirmado(pedido.claves(), "CN=" + ALIAS_COMPUTADOR);

        ConfiguracionArca config = actual();
        config.setPedidoCertificado(CertificadosArca.crearPkcs12(pedido.claves().getPrivate(), provisorio, clave));
        config.setPedidoFecha(LocalDateTime.now());
        configuracionRepository.save(config);

        return pedido.csrPem();
    }

    @Override
    public ConfiguracionArcaDto importarCertificado(byte[] contenido) {

        sesionUsuario.requerirPermiso(PermisoCodigo.FACTURACION_CONFIGURAR);

        if (contenido == null || contenido.length == 0) {
            throw new BusinessException("Debe seleccionar el certificado (.crt) descargado de ARCA.");
        }

        char[] clave = configuracionVigente.clave();
        ConfiguracionArca config = actual();

        if (config.getPedidoCertificado() == null) {
            throw new BusinessException("No hay un pedido de certificado pendiente. Genere el pedido, súbalo a "
                    + "ARCA y después importe el certificado que le devuelva.");
        }

        CredencialArca pedido = CertificadosArca.leerPkcs12(config.getPedidoCertificado(), clave);
        X509Certificate certificado = CertificadosArca.leerCertificado(contenido);

        if (!CertificadosArca.corresponde(certificado, pedido.certificado().getPublicKey())) {
            throw new BusinessException("El certificado no corresponde al último pedido generado. Importe el "
                    + "certificado que ARCA emitió para ese pedido, o genere un pedido nuevo.");
        }

        CredencialArca credencial = new CredencialArca(pedido.clavePrivada(), certificado);
        validarCertificado(credencial);

        ponerEnUso(config, credencial, clave);
        config.setPedidoCertificado(null);
        config.setPedidoFecha(null);

        return toDto(configuracionRepository.save(config));
    }

    @Override
    public ConfiguracionArcaDto importarPkcs12(byte[] archivo, char[] contrasenia) {

        sesionUsuario.requerirPermiso(PermisoCodigo.FACTURACION_CONFIGURAR);

        if (archivo == null || archivo.length == 0) {
            throw new BusinessException("Debe seleccionar el archivo del certificado (.p12 / .pfx).");
        }

        char[] clave = configuracionVigente.clave();
        CredencialArca credencial = CertificadosArca.leerPkcs12(archivo,
                contrasenia == null ? new char[0] : contrasenia);

        validarCertificado(credencial);

        ConfiguracionArca config = actual();
        ponerEnUso(config, credencial, clave);

        return toDto(configuracionRepository.save(config));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Override
    public PruebaConexionArca probarConexion() {

        sesionUsuario.requerirPermiso(PermisoCodigo.FACTURACION_CONFIGURAR);

        PasosPrueba pasos = new PasosPrueba();

        // 1. Configuración guardada en el sistema
        ContextoArca contexto;

        try {
            contexto = configuracionVigente.cargar(false);
        } catch (BusinessException e) {
            pasos.error(PASO_CONFIGURACION, e.getMessage());
            return pasos.terminar(PASO_SERVIDORES, PASO_CERTIFICADO, PASO_PUNTO_VENTA);
        }

        pasos.correcto(PASO_CONFIGURACION, contexto.ambiente().getDescripcion() + ", CUIT "
                + CuitUtils.formatear(contexto.cuit()) + ", punto de venta " + contexto.puntoVenta() + ".");

        String puntoVenta = PASO_PUNTO_VENTA + " " + contexto.puntoVenta();

        // 2. Servidores de ARCA (FEDummy)
        try {
            Wsfe.EstadoServidores estado = wsfe.estado(contexto.ambiente());

            if (!estado.todosOk()) {
                pasos.error(PASO_SERVIDORES, "Los servidores de ARCA no están funcionando (aplicación: "
                        + estado.appServer() + ", base de datos: " + estado.dbServer() + ", autenticación: "
                        + estado.authServer() + "). Es un problema de ARCA: intente más tarde.");
                return pasos.terminar(PASO_CERTIFICADO, puntoVenta);
            }
        } catch (BusinessException e) {
            pasos.error(PASO_SERVIDORES, sinConexion(e));
            return pasos.terminar(PASO_CERTIFICADO, puntoVenta);
        }

        pasos.correcto(PASO_SERVIDORES, "Los servidores de ARCA responden.");

        // 3. Certificado (ticket de acceso del WSAA)
        Wsfe.Auth auth;

        try {
            auth = tickets.obtener(contexto);
        } catch (ArcaComunicacionException e) {
            pasos.error(PASO_CERTIFICADO, sinConexion(e));
            return pasos.terminar(puntoVenta);
        } catch (BusinessException e) {
            pasos.error(PASO_CERTIFICADO, "ARCA no aceptó el certificado. " + e.getMessage());
            return pasos.terminar(puntoVenta);
        }

        pasos.correcto(PASO_CERTIFICADO, descripcionCertificado());

        // 4. Punto de venta habilitado para web services (último comprobante de cada factura)
        List<String> ultimos = new ArrayList<>();

        try {
            for (TipoComprobante tipo : facturasDelEmisor(contexto)) {
                long ultimo = wsfe.ultimoAutorizado(contexto.ambiente(), auth, contexto.puntoVenta(),
                        tipo.getCodigoArca());
                ultimos.add(tipo.getDescripcion() + ": "
                        + (ultimo == 0 ? "sin comprobantes autorizados" : "último N° " + ultimo));
            }
        } catch (ArcaComunicacionException e) {
            pasos.error(puntoVenta, sinConexion(e));
            return pasos.terminar();
        } catch (BusinessException e) {
            pasos.error(puntoVenta, "ARCA no aceptó el punto de venta " + contexto.puntoVenta() + ". "
                    + "Verifique en ARCA (\"Administración de puntos de venta y domicilios\") que exista, que "
                    + "no esté dado de baja y que sea del sistema \"Factura Electrónica - Web Services\" (los "
                    + "puntos de venta de \"Comprobantes en línea\" o de controlador fiscal no sirven). "
                    + "Detalle: " + e.getMessage());
            return pasos.terminar();
        }

        pasos.correcto(puntoVenta, String.join("; ", ultimos) + ".");

        return pasos.terminar();
    }

    /** Acumula los pasos de la prueba de conexión. */
    private static final class PasosPrueba {

        private final List<PruebaConexionArca.Paso> pasos = new ArrayList<>();

        void correcto(String nombre, String detalle) {
            pasos.add(new PruebaConexionArca.Paso(nombre, PruebaConexionArca.Resultado.CORRECTO, detalle));
        }

        void error(String nombre, String detalle) {
            pasos.add(new PruebaConexionArca.Paso(nombre, PruebaConexionArca.Resultado.ERROR, detalle));
        }

        /** Agrega como no verificados los pasos que quedaron sin probar. */
        PruebaConexionArca terminar(String... sinVerificar) {
            for (String nombre : sinVerificar) {
                pasos.add(new PruebaConexionArca.Paso(nombre, PruebaConexionArca.Resultado.SIN_VERIFICAR,
                        "No se verificó porque falló un paso anterior."));
            }
            return new PruebaConexionArca(pasos);
        }
    }

    private static String sinConexion(BusinessException e) {

        if (e instanceof ArcaComunicacionException) {
            return "No se pudo conectar con ARCA. Verifique la conexión a internet de esta PC (y que un "
                    + "firewall o antivirus no bloquee la aplicación). Si la conexión funciona, ARCA puede estar "
                    + "fuera de servicio: intente más tarde. Detalle: " + e.getMessage();
        }

        return e.getMessage();
    }

    private String descripcionCertificado() {

        ConfiguracionArca config = actual();

        String descripcion = "ARCA aceptó el certificado";

        if (config.getCertificadoVencimiento() != null) {
            descripcion += " (vence el " + config.getCertificadoVencimiento().toLocalDate()
                    .format(FORMATO_FECHA) + ")";
        }

        return descripcion + " y está autorizado para facturación electrónica.";
    }

    // ------------------------------------------------------------------

    private ConfiguracionArca actual() {
        return configuracionRepository.findFirstByOrderByIdAsc().orElseGet(() -> ConfiguracionArca.builder().build());
    }

    private Empresa empresa() {
        return empresaRepository.findFirstByOrderByIdAsc().orElseThrow(() -> new BusinessException(
                "No hay datos de la empresa cargados. Complételos en Configuración."));
    }

    private List<String> faltantesParaHabilitar(ConfiguracionArca config, Integer puntoVenta) {

        List<String> faltantes = new ArrayList<>();

        if (!claveCertificado.configurada()) {
            faltantes.add("Falta cargar en esta PC la clave del certificado.");
        }

        if (puntoVenta == null) {
            faltantes.add("Para habilitar la facturación electrónica debe indicar el punto de venta.");
        }

        if (config.getCertificado() == null) {
            faltantes.add("Para habilitar la facturación electrónica debe importar el certificado de ARCA.");
        } else if (config.getCertificadoVencimiento().isBefore(LocalDateTime.now())) {
            faltantes.add("El certificado de ARCA está vencido.");
        }

        empresaRepository.findFirstByOrderByIdAsc().ifPresentOrElse(empresa -> {

            if (empresa.getFechaInicioActividades() == null) {
                faltantes.add("Falta la fecha de inicio de actividades de la empresa (se imprime en las facturas).");
            }

            if (empresa.getCondicionIva() == null || !empresa.getCondicionIva().esDeEmisor()) {
                faltantes.add("La condición frente al IVA de la empresa debe ser responsable inscripto, "
                        + "monotributista o exento.");
            }
        }, () -> faltantes.add("Faltan los datos de la empresa."));

        return faltantes;
    }

    /** Controla que el certificado sea de la empresa y esté vigente. */
    private void validarCertificado(CredencialArca credencial) {

        if (credencial.vencido(Instant.now())) {
            throw new BusinessException("El certificado venció el "
                    + credencial.vencimiento().atZone(ZoneId.systemDefault()).toLocalDate() + ".");
        }

        String cuitEmpresa = CuitUtils.normalizar(empresa().getCuit());

        CertificadosArca.cuitDelTitular(credencial.certificado()).ifPresent(cuit -> {
            if (!cuit.equals(cuitEmpresa)) {
                throw new BusinessException("El certificado es del CUIT " + CuitUtils.formatear(cuit)
                        + " y la empresa tiene el CUIT " + CuitUtils.formatear(cuitEmpresa) + ".");
            }
        });
    }

    /** Guarda el certificado cifrado y descarta los tickets de acceso del certificado anterior. */
    private void ponerEnUso(ConfiguracionArca config, CredencialArca credencial, char[] clave) {

        config.setCertificado(CertificadosArca.crearPkcs12(credencial.clavePrivada(), credencial.certificado(), clave));
        config.setCertificadoTitular(recortar(CertificadosArca.titular(credencial.certificado())));
        config.setCertificadoVencimiento(LocalDateTime.ofInstant(credencial.vencimiento(), ZoneId.systemDefault()));

        ticketRepository.borrarTodos();
    }

    private static List<TipoComprobante> facturasDelEmisor(ContextoArca contexto) {
        return switch (contexto.condicionIva()) {
            case RESPONSABLE_INSCRIPTO -> List.of(TipoComprobante.FACTURA_A, TipoComprobante.FACTURA_B);
            default -> List.of(TipoComprobante.FACTURA_C);
        };
    }

    private static String recortar(String texto) {
        return texto.length() <= 255 ? texto : texto.substring(0, 254) + "…";
    }

    /** El certificado en uso o, si todavía no hay, el pedido pendiente; null si no hay nada guardado. */
    private static byte[] guardado(ConfiguracionArca config) {
        return config.getCertificado() != null ? config.getCertificado() : config.getPedidoCertificado();
    }

    private static boolean abre(byte[] pkcs12, char[] clave) {
        try {
            CertificadosArca.leerPkcs12(pkcs12, clave);
            return true;
        } catch (ArcaException e) {
            return false;
        }
    }

    private Boolean claveCorrecta(ConfiguracionArca config) {

        byte[] guardado = guardado(config);

        if (guardado == null) {
            return null;
        }

        return claveCertificado.obtener().map(clave -> abre(guardado, clave)).orElse(null);
    }

    private ConfiguracionArcaDto toDto(ConfiguracionArca c) {

        return ConfiguracionArcaDto.builder()
                .ambiente(c.getAmbiente() == null ? AmbienteArca.HOMOLOGACION : c.getAmbiente())
                .puntoVenta(c.getPuntoVenta())
                .habilitada(Boolean.TRUE.equals(c.getHabilitada()))
                .claveConfigurada(claveCertificado.configurada())
                .origenClave(claveCertificado.origen())
                .claveCorrecta(claveCorrecta(c))
                .certificadoCargado(c.getCertificado() != null)
                .certificadoTitular(c.getCertificadoTitular())
                .certificadoVencimiento(c.getCertificadoVencimiento())
                .pedidoPendiente(c.getPedidoCertificado() != null)
                .pedidoFecha(c.getPedidoFecha())
                .build();
    }

}
