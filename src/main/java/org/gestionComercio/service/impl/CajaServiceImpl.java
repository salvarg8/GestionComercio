package org.gestionComercio.service.impl;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.dto.caja.MovimientoCajaDto;
import org.gestionComercio.entity.Caja;
import org.gestionComercio.entity.MovimientoCaja;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.mapper.CajaMapper;
import org.gestionComercio.mapper.MovimientoCajaMapper;
import org.gestionComercio.repository.CajaRepository;
import org.gestionComercio.repository.MovimientoCajaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.CajaService;
import org.gestionComercio.util.CurrencyUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    @Override
    public CajaDto abrir(BigDecimal saldoInicial) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_ABRIR);

        BigDecimal saldo = validarImporte(saldoInicial, "El saldo inicial", true);
        Usuario usuario = sesionUsuario.getUsuario();

        if (cajaRepository.existsByUsuarioIdAndEstadoCaja(usuario.getId(), EstadoCaja.ABIERTA)) {
            throw new BusinessException("Ya tiene una caja abierta. Debe cerrarla antes de abrir otra.");
        }

        LocalDateTime ahora = LocalDateTime.now();

        Caja caja = cajaRepository.save(Caja.builder()
                .fechaApertura(ahora)
                .saldoInicial(saldo)
                .estadoCaja(EstadoCaja.ABIERTA)
                .usuario(usuario)
                .build());

        MovimientoCaja apertura = movimientoCajaRepository.save(MovimientoCaja.builder()
                .fecha(ahora)
                .tipo(TipoMovimientoCaja.APERTURA)
                .descripcion("Apertura de caja")
                .importe(saldo)
                .caja(caja)
                .usuario(usuario)
                .build());

        return conResumen(caja, List.of(apertura));
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
    public List<MovimientoCajaDto> listarMovimientos(Long cajaId) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_VER);

        if (cajaId == null) {
            throw new BusinessException("La caja es obligatoria.");
        }

        if (!cajaRepository.existsById(cajaId)) {
            throw new EntityNotFoundException("No existe una caja con ID: " + cajaId);
        }

        return movimientoCajaMapper.toDtoList(movimientoCajaRepository.findByCajaIdOrderByFechaAscIdAsc(cajaId));
    }

    @Override
    public CajaDto cerrar(BigDecimal saldoContado, String observacion) {

        sesionUsuario.requerirPermiso(PermisoCodigo.CAJA_CERRAR);

        BigDecimal contado = validarImporte(saldoContado, "El saldo contado", true);
        String detalle = validarTexto(observacion, "La observación");

        Caja caja = requerirCajaAbierta();
        List<MovimientoCaja> movimientos = movimientosDe(caja);
        BigDecimal esperado = Resumen.de(caja.getSaldoInicial(), movimientos).saldoEsperado();

        caja.setFechaCierre(LocalDateTime.now());
        caja.setSaldoEsperado(esperado);
        caja.setSaldoFinal(contado);
        caja.setDiferencia(contado.subtract(esperado));
        caja.setObservacionCierre(detalle);
        caja.setEstadoCaja(EstadoCaja.CERRADA);

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
