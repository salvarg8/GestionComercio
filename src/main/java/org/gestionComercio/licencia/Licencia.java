package org.gestionComercio.licencia;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Datos de una licencia: para qué comercio es, hasta cuándo vale, qué módulos
 * habilita y en qué PC funciona. Sólo vale si la firma la verificó
 * {@link VerificadorLicencia}; el formato lo define {@link ClaveLicencia}.
 *
 * @param id          identificador de la licencia emitida
 * @param cliente     razón social del comercio
 * @param cuit        CUIT del comercio (sólo dígitos)
 * @param emitida     momento de emisión; una licencia más nueva reemplaza a la anterior
 * @param vencimiento último día en que vale, o null si no vence (pago único)
 * @param modulos     módulos habilitados
 * @param equipos     códigos de las PC habilitadas (ver {@link IdentificadorEquipo})
 */
public record Licencia(String id,
                       String cliente,
                       String cuit,
                       LocalDateTime emitida,
                       LocalDate vencimiento,
                       Set<ModuloLicencia> modulos,
                       List<String> equipos) {

    public Licencia {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cliente, "cliente");
        Objects.requireNonNull(cuit, "cuit");
        Objects.requireNonNull(emitida, "emitida");
        modulos = modulos == null || modulos.isEmpty()
                ? Set.copyOf(EnumSet.noneOf(ModuloLicencia.class))
                : Set.copyOf(EnumSet.copyOf(modulos));
        equipos = equipos == null
                ? List.of()
                : equipos.stream().map(IdentificadorEquipo::normalizar).distinct().toList();
    }

    /** @return true si es de pago único (no vence). */
    public boolean perpetua() {
        return vencimiento == null;
    }

    public boolean incluyeEquipo(String codigo) {
        return codigo != null && equipos.contains(IdentificadorEquipo.normalizar(codigo));
    }

    public boolean tieneModulo(ModuloLicencia modulo) {
        return modulos.contains(modulo);
    }

}
