package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.dto.caja.MovimientoCajaDto;
import org.gestionComercio.dto.caja.ResponsableCajaDto;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.EstadoRegistro;
import org.gestionComercio.enums.EstadoUsuario;
import org.gestionComercio.enums.FiltroCaja;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.mapper.CajaMapper;
import org.gestionComercio.mapper.MovimientoCajaMapper;
import org.gestionComercio.repository.CajaRepository;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.repository.UsuarioRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.service.CajaService;
import org.gestionComercio.util.CurrencyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Cualquier excepción revierte la transacción completa.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CajaServiceImpl implements CajaService {

    /** Mayor importe que admite una columna precision 12, scale 2. */
    static final BigDecimal IMPORTE_MAXIMO = new BigDecimal("9999999999.99");

    static final int TEXTO_MAX = 255;

    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final CajaMapper cajaMapper;
    private final MovimientoCajaMapper movimientoCajaMapper;
    private final SesionUsuario sesionUsuario;
    private final UsuarioRepository usuarioRepository;

    @Override
    public CajaDto abrir(BigDecimal saldoInicial) {
        return abrir(null, saldoInicial);
    }

    @Override
    public CajaDto abrir(Long responsableId, BigDecimal saldoInicial) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_ABRIR);

        BigDecimal saldo = validarImporte(saldoInicial, "El saldo inicial", true);
        Usuario operador = sesionUsuario.getUsuario();
        Usuario responsable;

        if (responsableId == null || responsableId.equals(operador.getId())) {

            responsable = operador;

            if (cajaRepository.existsByUsuarioIdAndEstadoCaja(responsable.getId(), EstadoCaja.ABIERTA)) {
                throw new BusinessException("Ya tiene una caja abierta. Debe cerrarla antes de abrir otra.");
            }

        } else {

            sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_ABRIR_OTRO_USUARIO);

            responsable = usuarioRepository.findById(responsableId)
                    .orElseThrow(() -> new EntityNotFoundException("No existe un usuario con ID: " + responsableId));

            if (!estaActivo(responsable)) {
                throw new BusinessException("El usuario " + nombre(responsable)
                        + " no está activo. No se le puede abrir una caja.");
            }

            if (cajaRepository.existsByUsuarioIdAndEstadoCaja(responsable.getId(), EstadoCaja.ABIERTA)) {
                throw new BusinessException("El usuario " + nombre(responsable)
                        + " ya tiene una caja abierta. Debe cerrarse antes de abrirle otra.");
            }
        }

        LocalDateTime ahora = LocalDateTime.now();

        Caja caja = cajaRepository.save(Caja.builder()
                .fechaApertura(ahora)
                .saldoInicial(saldo)
                .estadoCaja(EstadoCaja.ABIERTA)
                .usuario(responsable)
                .usuarioApertura(operador)
                .build());

        MovimientoCaja apertura = movimientoCajaRepository.save(MovimientoCaja.builder()
                .fecha(ahora)
                .tipo(TipoMovimientoCaja.APERTURA)
                .descripcion("Apertura de caja")
                .importe(saldo)
                .caja(caja)
                .usuario(operador)
                .build());

        return conResumen(caja, List.of(apertura));
    }

    @Transactional(readOnly = true)
    @Override
    public List<ResponsableCajaDto> listarResponsablesDisponibles() {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_ABRIR_OTRO_USUARIO);

        return usuarioRepository
                .findByEstadoRegistroAndEstadoUsuarioOrderByApellidoAscNombreAsc(
                        EstadoRegistro.ACTIVO, EstadoUsuario.ACTIVO)
                .stream()
                .map(u -> new ResponsableCajaDto(u.getId(), u.getUsuario(), u.getNombre() + " " + u.getApellido()))
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<CajaDto> obtenerCajaAbierta() {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_VER);

        return buscarCajaAbierta().map(caja -> conResumen(caja, movimientosDe(caja)));
    }

    @Override
    public MovimientoCajaDto registrarMovimiento(TipoMovimientoCaja tipo, BigDecimal importe, String descripcion) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_REGISTRAR_MOVIMIENTO);

        if (tipo == null) {
            throw new BusinessException("Debe indicar el tipo de movimiento.");
        }

        if (!tipo.esManual()) {
            throw new BusinessException("Los movimientos de tipo \"" + tipo.getDescripcion()
                    + "\" no se pueden registrar a mano.");
        }

        BigDecimal monto = validarImporte(importe, "El importe", false);
        String motivo = validarTexto(descripcion, "La descripción");

        if (motivo == null) {
            throw new BusinessException("Debe indicar la descripción del movimiento.");
        }

        Caja caja = requerirCajaAbierta();

        if (tipo.esEgreso()) {

            BigDecimal saldo = Resumen.de(caja.getSaldoInicial(), movimientosDe(caja)).saldoEsperado();

            if (monto.compareTo(saldo) > 0) {
                throw new BusinessException("El importe (" + CurrencyUtils.format(monto)
                        + ") supera el saldo disponible en caja (" + CurrencyUtils.format(saldo) + ").");
            }
        }

        MovimientoCaja movimiento = movimientoCajaRepository.save(MovimientoCaja.builder()
                .fecha(LocalDateTime.now())
                .tipo(tipo)
                .descripcion(motivo)
                .importe(monto)
                .caja(caja)
                .usuario(sesionUsuario.getUsuario())
                .build());

        return movimientoCajaMapper.toDto(movimiento);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CajaDto> listarCajasCerradas(LocalDate desde, LocalDate hasta) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_VER);

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }

        return conResumen(cerradasVisibles(desde, hasta));
    }

    @Transactional(readOnly = true)
    @Override
    public List<CajaDto> listarHistorial(LocalDate desde, LocalDate hasta, FiltroCaja filtro) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_VER);

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new BusinessException("La fecha desde no puede ser posterior a la fecha hasta.");
        }

        FiltroCaja estado = filtro == null ? FiltroCaja.TODAS : filtro;
        List<CajaDto> historial = new ArrayList<>();

        if (estado != FiltroCaja.CERRADAS) {

            LocalDateTime limite = DateUtils.finDelDia(hasta);

            historial.addAll(conResumen(cajaRepository.findAbiertas().stream()
                    .filter(this::puedeVer)
                    .filter(caja -> limite == null || !caja.getFechaApertura().isAfter(limite))
                    .sorted(Comparator.comparing(Caja::getFechaApertura).reversed())
                    .toList()));
        }

        if (estado != FiltroCaja.ABIERTAS) {
            historial.addAll(conResumen(cerradasVisibles(desde, hasta)));
        }

        return historial;
    }

    /** Cajas cerradas en el rango que la sesión puede ver (ver {@link #puedeVer}). */
    private List<Caja> cerradasVisibles(LocalDate desde, LocalDate hasta) {
        return cajaRepository.findCerradas(
                        desde == null ? null : desde.atStartOfDay(),
                        DateUtils.finDelDia(hasta))
                .stream()
                .filter(this::puedeVer)
                .toList();
    }

    /**
     * Si la sesión puede ver la caja y sus movimientos: la propia siempre;
     * la de otro usuario con CAJA_VER_ABIERTAS_TODAS o CAJA_VER_CERRADAS_TODAS,
     * según su estado.
     */
    private boolean puedeVer(Caja caja) {

        if (esDelUsuarioDeLaSesion(caja)) {
            return true;
        }

        return sesionUsuario.tienePermiso(caja.getEstadoCaja() == EstadoCaja.ABIERTA
                ? PermisoCodigo.CAJA_VER_ABIERTAS_TODAS
                : PermisoCodigo.CAJA_VER_CERRADAS_TODAS);
    }

    @Transactional(readOnly = true)
    @Override
    public List<CajaDto> listarCajasAbiertasDeOtrosUsuarios() {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_CERRAR_OTRO_USUARIO);

        return conResumen(cajaRepository.findAbiertas().stream()
                .filter(caja -> !esDelUsuarioDeLaSesion(caja))
                .toList());
    }

    @Transactional(readOnly = true)
    @Override
    public List<MovimientoCajaDto> listarMovimientos(Long cajaId) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_VER);

        if (cajaId == null) {
            throw new BusinessException("La caja es obligatoria.");
        }

        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new EntityNotFoundException("No existe una caja con ID: " + cajaId));

        // Quien puede cerrar las cajas abiertas de otros también ve sus movimientos.
        boolean cierraAjena = caja.getEstadoCaja() == EstadoCaja.ABIERTA
                && sesionUsuario.tienePermiso(PermisoCodigo.CAJA_CERRAR_OTRO_USUARIO);

        if (!puedeVer(caja) && !cierraAjena) {
            throw new AuthorizationException("No tiene permiso para ver los movimientos de esta caja.");
        }

        return movimientoCajaMapper.toDtoList(movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(cajaId));
    }

    @Override
    public CajaDto cerrar(BigDecimal saldoContado, String observacion) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_CERRAR);

        BigDecimal contado = validarImporte(saldoContado, "El saldo contado", true);
        String detalle = validarTexto(observacion, "La observación");

        return cerrarConArqueo(requerirCajaAbierta(), contado, detalle);
    }

    @Override
    public CajaDto cerrar(Long cajaId, BigDecimal saldoContado, String observacion) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_CERRAR);

        BigDecimal contado = validarImporte(saldoContado, "El saldo contado", true);
        String detalle = validarTexto(observacion, "La observación");

        if (cajaId == null) {
            throw new BusinessException("La caja es obligatoria.");
        }

        Caja caja = cajaRepository.findById(cajaId)
                .orElseThrow(() -> new EntityNotFoundException("No existe una caja con ID: " + cajaId));

        if (!esDelUsuarioDeLaSesion(caja)) {
            sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_CERRAR_OTRO_USUARIO);
        }

        if (caja.getEstadoCaja() != EstadoCaja.ABIERTA) {
            throw new BusinessException("La caja ya está cerrada.");
        }

        return cerrarConArqueo(caja, contado, detalle);
    }

    /** Guarda el arqueo y deja la caja cerrada por el usuario de la sesión. */
    private CajaDto cerrarConArqueo(Caja caja, BigDecimal contado, String detalle) {

        List<MovimientoCaja> movimientos = movimientosDe(caja);
        BigDecimal esperado = Resumen.de(caja.getSaldoInicial(), movimientos).saldoEsperado();

        caja.setFechaCierre(LocalDateTime.now());
        caja.setSaldoEsperado(esperado);
        caja.setSaldoFinal(contado);
        caja.setDiferencia(contado.subtract(esperado));
        caja.setObservacionCierre(detalle);
        caja.setEstadoCaja(EstadoCaja.CERRADA);
        caja.setUsuarioCierre(sesionUsuario.getUsuario());

        return conResumen(cajaRepository.save(caja), movimientos);
    }

    private Optional<Caja> buscarCajaAbierta() {

        return cajaRepository.findFirstByUsuarioIdAndEstadoCajaOrderByFechaAperturaDesc(
                sesionUsuario.getUsuario().getId(), EstadoCaja.ABIERTA);
    }

    private Caja requerirCajaAbierta() {

        return buscarCajaAbierta().orElseThrow(() -> new BusinessException(
                "No tiene una caja abierta. Debe abrir la caja antes de operar."));
    }

    private List<MovimientoCaja> movimientosDe(Caja caja) {
        return movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(caja.getId());
    }

    private boolean esDelUsuarioDeLaSesion(Caja caja) {
        return caja.getUsuario().getId().equals(sesionUsuario.getUsuario().getId());
    }

    private static boolean estaActivo(Usuario usuario) {
        return usuario.getEstadoRegistro() == EstadoRegistro.ACTIVO
                && usuario.getEstadoUsuario() == EstadoUsuario.ACTIVO;
    }

    private static String nombre(Usuario usuario) {
        return usuario.getNombre() + " " + usuario.getApellido() + " (" + usuario.getUsuario() + ")";
    }

    /** Resumen de varias cajas, consultando los movimientos de todas juntas. */
    private List<CajaDto> conResumen(List<Caja> cajas) {

        if (cajas.isEmpty()) {
            return List.of();
        }

        Map<Long, List<MovimientoCaja>> movimientosPorCaja = movimientoCajaRepository
                .findByCajaIdIn(cajas.stream().map(Caja::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(m -> m.getCaja().getId()));

        return cajas.stream()
                .map(caja -> conResumen(caja, movimientosPorCaja.getOrDefault(caja.getId(), List.of())))
                .toList();
    }

    private CajaDto conResumen(Caja caja, List<MovimientoCaja> movimientos) {

        Resumen resumen = Resumen.de(caja.getSaldoInicial(), movimientos);
        CajaDto dto = cajaMapper.toDto(caja);

        dto.setTotalIngresos(resumen.ingresos());
        dto.setTotalEgresos(resumen.egresos());

        // Una caja cerrada conserva el saldo esperado calculado al cerrarla.
        if (caja.getEstadoCaja() != EstadoCaja.CERRADA || caja.getSaldoEsperado() == null) {
            dto.setSaldoEsperado(resumen.saldoEsperado());
        }

        return dto;
    }

    /**
     * @param permitirCero true para saldos (cero o más), false para importes de movimientos (más que cero)
     * @return el importe con escala 2
     */
    private BigDecimal validarImporte(BigDecimal importe, String campo, boolean permitirCero) {

        if (importe == null) {
            throw new BusinessException(campo + " es obligatorio.");
        }

        if (permitirCero ? importe.signum() < 0 : importe.signum() <= 0) {
            throw new BusinessException(campo + (permitirCero
                    ? " no puede ser negativo." : " debe ser mayor que cero."));
        }

        if (importe.stripTrailingZeros().scale() > 2) {
            throw new BusinessException(campo + " admite hasta dos decimales.");
        }

        if (importe.compareTo(IMPORTE_MAXIMO) > 0) {
            throw new BusinessException(campo + " supera el máximo permitido ("
                    + CurrencyUtils.format(IMPORTE_MAXIMO) + ").");
        }

        return importe.setScale(2);
    }

    /** @return el texto recortado, o null si está vacío. */
    private String validarTexto(String texto, String campo) {

        if (texto == null || texto.isBlank()) {
            return null;
        }

        String recortado = texto.trim();

        if (recortado.length() > TEXTO_MAX) {
            throw new BusinessException(campo + " no puede superar los " + TEXTO_MAX + " caracteres.");
        }

        return recortado;
    }

    /** Totales de una caja. La apertura no cuenta como ingreso: es el saldo inicial. */
    private record Resumen(BigDecimal ingresos, BigDecimal egresos, BigDecimal saldoEsperado) {

        static Resumen de(BigDecimal saldoInicial, List<MovimientoCaja> movimientos) {

            BigDecimal ingresos = sumar(movimientos, true);
            BigDecimal egresos = sumar(movimientos, false);

            return new Resumen(ingresos, egresos, saldoInicial.add(ingresos).subtract(egresos));
        }

        private static BigDecimal sumar(List<MovimientoCaja> movimientos, boolean ingresos) {

            return movimientos.stream()
                    .filter(m -> ingresos ? m.getTipo().esIngreso() : m.getTipo().esEgreso())
                    .map(MovimientoCaja::getImporte)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2);
        }
    }

}
