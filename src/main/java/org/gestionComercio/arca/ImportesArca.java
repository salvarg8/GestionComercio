package org.gestionComercio.arca;

import org.gestionComercio.enums.AlicuotaIVA;
import org.gestionComercio.enums.TipoComprobante;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Importes de un comprobante electrónico a partir de los renglones de la
 * venta (precios finales, con IVA incluido) y del descuento general.
 *
 * <p>El descuento se reparte entre las alícuotas en proporción a su importe
 * (la última se lleva la diferencia de redondeo). En cada alícuota el neto es
 * importe / (1 + alícuota) redondeado a 2 decimales y el IVA, la diferencia:
 * así neto + IVA + exento cuadra siempre al centavo con el total.
 *
 * <p>En las facturas y notas de crédito C no se discrimina IVA: el neto es el
 * total.
 */
public final class ImportesArca {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal CERO = BigDecimal.ZERO.setScale(2);

    private ImportesArca() {
    }

    /** Importe final (con IVA) de un renglón y su alícuota. */
    public record Renglon(AlicuotaIVA alicuota, BigDecimal importe) {
    }

    public record Importes(
            BigDecimal total,
            BigDecimal neto,
            BigDecimal iva,
            BigDecimal exento,
            List<Wsfe.AlicuotaIva> alicuotas
    ) {
    }

    public static Importes calcular(TipoComprobante tipo, List<Renglon> renglones, BigDecimal descuento) {

        Map<AlicuotaIVA, BigDecimal> porAlicuota = new EnumMap<>(AlicuotaIVA.class);

        for (Renglon r : renglones) {
            porAlicuota.merge(r.alicuota() == null ? AlicuotaIVA.IVA_21 : r.alicuota(),
                    r.importe().setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
        }

        BigDecimal bruto = porAlicuota.values().stream().reduce(CERO, BigDecimal::add);
        BigDecimal desc = descuento == null ? CERO : descuento.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = bruto.subtract(desc);

        if (total.signum() < 0) {
            throw new IllegalArgumentException("El descuento supera el importe de los renglones.");
        }

        if (tipo.getLetra() != null && tipo.getLetra() == 'C') {
            return new Importes(total, total, CERO, CERO, List.of());
        }

        Map<AlicuotaIVA, BigDecimal> conDescuento = repartirDescuento(porAlicuota, bruto, desc);

        BigDecimal neto = CERO;
        BigDecimal iva = CERO;
        BigDecimal exento = CERO;
        List<Wsfe.AlicuotaIva> alicuotas = new ArrayList<>();

        for (Map.Entry<AlicuotaIVA, BigDecimal> e : conDescuento.entrySet()) {

            AlicuotaIVA alicuota = e.getKey();
            BigDecimal importe = e.getValue();

            if (alicuota.getCodigoArca() == null) {
                exento = exento.add(importe);
                continue;
            }

            BigDecimal divisor = BigDecimal.ONE.add(alicuota.getPorcentaje().divide(CIEN));
            BigDecimal base = importe.divide(divisor, 2, RoundingMode.HALF_UP);
            BigDecimal impuesto = importe.subtract(base);

            if (importe.signum() == 0) {
                continue;
            }

            alicuotas.add(new Wsfe.AlicuotaIva(alicuota.getCodigoArca(), base, impuesto));
            neto = neto.add(base);
            iva = iva.add(impuesto);
        }

        return new Importes(total, neto, iva, exento, List.copyOf(alicuotas));
    }

    /** Importe de cada alícuota menos su parte proporcional del descuento. */
    private static Map<AlicuotaIVA, BigDecimal> repartirDescuento(
            Map<AlicuotaIVA, BigDecimal> porAlicuota, BigDecimal bruto, BigDecimal descuento) {

        Map<AlicuotaIVA, BigDecimal> resultado = new EnumMap<>(AlicuotaIVA.class);

        if (descuento.signum() == 0 || bruto.signum() == 0) {
            resultado.putAll(porAlicuota);
            return resultado;
        }

        BigDecimal repartido = CERO;
        List<AlicuotaIVA> claves = new ArrayList<>(porAlicuota.keySet());

        for (int i = 0; i < claves.size(); i++) {

            AlicuotaIVA alicuota = claves.get(i);
            BigDecimal importe = porAlicuota.get(alicuota);

            BigDecimal parte = i == claves.size() - 1
                    ? descuento.subtract(repartido)
                    : descuento.multiply(importe).divide(bruto, 2, RoundingMode.HALF_UP);

            repartido = repartido.add(parte);
            resultado.put(alicuota, importe.subtract(parte));
        }

        return resultado;
    }

}
