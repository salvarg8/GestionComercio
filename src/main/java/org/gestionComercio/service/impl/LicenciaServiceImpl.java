package org.gestionComercio.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.gestionComercio.entity.RegistroLicencia;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.licencia.ClaveLicencia;
import org.gestionComercio.licencia.EstadoLicencia;
import org.gestionComercio.licencia.IdentificadorEquipo;
import org.gestionComercio.licencia.Licencia;
import org.gestionComercio.licencia.LicenciaInvalidaException;
import org.gestionComercio.licencia.ModuloLicencia;
import org.gestionComercio.licencia.ModulosHabilitados;
import org.gestionComercio.licencia.SituacionLicencia;
import org.gestionComercio.licencia.VerificadorLicencia;
import org.gestionComercio.repository.RegistroLicenciaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.LicenciaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Licencia del comercio. Funciona sin conexión: la firma se verifica con la
 * clave pública incluida en la aplicación y la PC se reconoce por su código.
 *
 * <ul>
 *     <li>Sin licencia, el sistema funciona completo durante {@link #DIAS_PRUEBA} días
 *         desde la instalación.</li>
 *     <li>Una licencia con vencimiento avisa {@link #DIAS_AVISO} días antes y, al vencer,
 *         deja el sistema en modo sólo consulta.</li>
 *     <li>Si la fecha de la PC es anterior a la última en que se usó el sistema (reloj
 *         atrasado para estirar el vencimiento), también queda en sólo consulta.</li>
 * </ul>
 */
@Slf4j
@Service
public class LicenciaServiceImpl implements LicenciaService, ModulosHabilitados {

    public static final int DIAS_PRUEBA = 30;

    public static final int DIAS_AVISO = 15;

    /** Días que la fecha de la PC puede estar atrás de la última registrada (relojes de distintas PC). */
    static final int TOLERANCIA_RELOJ = 1;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RegistroLicenciaRepository repository;
    private final VerificadorLicencia verificador;
    private final IdentificadorEquipo identificadorEquipo;
    private final SesionUsuario sesionUsuario;
    private final Clock clock;

    @Autowired
    public LicenciaServiceImpl(RegistroLicenciaRepository repository,
                               VerificadorLicencia verificador,
                               IdentificadorEquipo identificadorEquipo,
                               SesionUsuario sesionUsuario) {
        this(repository, verificador, identificadorEquipo, sesionUsuario, Clock.systemDefaultZone());
    }

    LicenciaServiceImpl(RegistroLicenciaRepository repository,
                        VerificadorLicencia verificador,
                        IdentificadorEquipo identificadorEquipo,
                        SesionUsuario sesionUsuario,
                        Clock clock) {
        this.repository = repository;
        this.verificador = verificador;
        this.identificadorEquipo = identificadorEquipo;
        this.sesionUsuario = sesionUsuario;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public EstadoLicencia estado() {
        return evaluar(registro(), LocalDate.now(clock));
    }

    @Override
    @Transactional
    public EstadoLicencia verificarAlIniciarSesion() {

        LocalDate hoy = LocalDate.now(clock);
        RegistroLicencia registro = registro();

        // Se evalúa con la fecha guardada antes de actualizarla.
        EstadoLicencia estado = evaluar(registro, hoy);

        repository.registrarUso(RegistroLicencia.ID, hoy);

        if (estado.requiereAviso()) {
            log.info("Licencia: {} - {}", estado.situacion(), estado.mensaje());
        }

        return estado;
    }

    @Override
    @Transactional
    public EstadoLicencia activar(String clave) {

        sesionUsuario.requerirPermiso(PermisoCodigo.LICENCIA_ADMINISTRAR);

        if (clave == null || clave.isBlank()) {
            throw new BusinessException("Ingrese la clave de licencia o cargue el archivo de licencia.");
        }

        Licencia licencia = verificador.verificar(clave);
        LocalDate hoy = LocalDate.now(clock);
        String equipo = codigoEquipo();

        if (!licencia.incluyeEquipo(equipo)) {
            throw new BusinessException("La licencia no incluye esta PC (código " + equipo + "). "
                    + "Verifique que sea la licencia de este comercio o pida una que incluya este código.");
        }

        if (!licencia.perpetua() && hoy.isAfter(licencia.vencimiento())) {
            throw new BusinessException("La licencia venció el " + FORMATO_FECHA.format(licencia.vencimiento())
                    + ". Pida a su proveedor una licencia renovada.");
        }

        RegistroLicencia registro = registro();

        Licencia actual = licenciaValida(registro.getClave());
        if (actual != null && licencia.emitida().isBefore(actual.emitida())) {
            throw new BusinessException("La licencia es anterior a la que está activa (emitida el "
                    + FORMATO_FECHA.format(actual.emitida()) + "). Use la licencia más reciente.");
        }

        // Una licencia nueva del proveedor reinicia la última fecha registrada: así se resuelve el caso de
        // una PC que tuvo la fecha adelantada por error. Volver a activar la misma licencia no la reinicia.
        if (actual == null || !actual.id().equals(licencia.id())) {
            registro.setUltimaFecha(hoy);
        }

        registro.setClave(ClaveLicencia.normalizar(clave));
        registro.setFechaActivacion(LocalDateTime.now(clock));
        repository.save(registro);

        log.info("Licencia activada: {} (CUIT {}), {}, {} equipo(s).", licencia.cliente(), licencia.cuit(),
                licencia.perpetua() ? "sin vencimiento" : "vence " + licencia.vencimiento(),
                licencia.equipos().size());

        return evaluar(registro, hoy);
    }

    @Override
    public String codigoEquipo() {
        return identificadorEquipo.obtener();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean habilitado(ModuloLicencia modulo) {
        return estado().tieneModulo(modulo);
    }

    // ------------------------------------------------------------------

    private RegistroLicencia registro() {
        return repository.findById(RegistroLicencia.ID).orElseThrow(() -> new BusinessException(
                "Falta el registro de la licencia en la base de datos. Comuníquese con soporte."));
    }

    EstadoLicencia evaluar(RegistroLicencia registro, LocalDate hoy) {

        String equipo = codigoEquipo();
        Licencia licencia = null;

        if (registro.getClave() != null) {
            try {
                licencia = verificador.verificar(registro.getClave());
            } catch (LicenciaInvalidaException e) {
                return new EstadoLicencia(SituacionLicencia.INVALIDA, null, equipo, null, null, hoy, e.getMessage());
            }
        }

        // Con una licencia sin vencimiento la fecha no importa.
        boolean dependeDeLaFecha = licencia == null || !licencia.perpetua();
        LocalDate ultimaFecha = registro.getUltimaFecha();

        if (dependeDeLaFecha && ultimaFecha != null && hoy.isBefore(ultimaFecha.minusDays(TOLERANCIA_RELOJ))) {
            return new EstadoLicencia(SituacionLicencia.RELOJ_ATRASADO, licencia, equipo, null, null, hoy,
                    FORMATO_FECHA.format(ultimaFecha));
        }

        if (licencia == null) {
            LocalDate finPrueba = registro.getInicioPrueba().plusDays(DIAS_PRUEBA);
            long dias = ChronoUnit.DAYS.between(hoy, finPrueba);
            SituacionLicencia situacion = dias > 0 ? SituacionLicencia.PRUEBA : SituacionLicencia.PRUEBA_VENCIDA;
            return new EstadoLicencia(situacion, null, equipo, finPrueba, Math.max(dias, 0), hoy, null);
        }

        if (!licencia.incluyeEquipo(equipo)) {
            return new EstadoLicencia(SituacionLicencia.EQUIPO_NO_HABILITADO, licencia, equipo, null, null, hoy, null);
        }

        if (licencia.perpetua()) {
            return new EstadoLicencia(SituacionLicencia.ACTIVA, licencia, equipo, null, null, hoy, null);
        }

        long dias = ChronoUnit.DAYS.between(hoy, licencia.vencimiento());
        SituacionLicencia situacion = dias < 0
                ? SituacionLicencia.VENCIDA
                : dias <= DIAS_AVISO ? SituacionLicencia.POR_VENCER : SituacionLicencia.ACTIVA;

        return new EstadoLicencia(situacion, licencia, equipo, null, Math.max(dias, 0), hoy, null);
    }

    private Licencia licenciaValida(String clave) {
        if (clave == null) {
            return null;
        }
        try {
            return verificador.verificar(clave);
        } catch (LicenciaInvalidaException e) {
            return null;
        }
    }

}
