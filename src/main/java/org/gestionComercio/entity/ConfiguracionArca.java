package org.gestionComercio.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.gestionComercio.enums.AmbienteArca;

import java.time.LocalDateTime;

/**
 * Configuración de la facturación electrónica de la empresa (un único
 * registro). Está en la base para que todas las PC del comercio la compartan.
 *
 * <p>El certificado y su clave privada se guardan en un almacén PKCS#12
 * cifrado con la clave {@code arca.clave-certificado} (variable de entorno
 * {@code ARCA_CLAVE_CERTIFICADO}), que no está en la base.
 *
 * <p>Renovar el certificado no interrumpe la facturación: la clave nueva y su
 * pedido quedan en {@link #pedidoCertificado} hasta que se importa el
 * certificado firmado por ARCA, y recién ahí reemplazan al actual.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Entity
@Table(name = "configuracion_arca")
public class ConfiguracionArca extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AmbienteArca ambiente = AmbienteArca.HOMOLOGACION;

    /** Punto de venta exclusivo para web services, dado de alta en ARCA. */
    private Integer puntoVenta;

    /** Si está deshabilitada no se pueden registrar facturas. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean habilitada = false;

    /** PKCS#12 cifrado con el certificado vigente y su clave privada. */
    @Basic(fetch = FetchType.LAZY)
    private byte[] certificado;

    /** Titular (subject) del certificado vigente. */
    @Column(length = 255)
    private String certificadoTitular;

    private LocalDateTime certificadoVencimiento;

    /**
     * PKCS#12 cifrado con la clave privada generada para un pedido de
     * certificado (CSR) cuyo certificado todavía no se importó.
     */
    @Basic(fetch = FetchType.LAZY)
    private byte[] pedidoCertificado;

    private LocalDateTime pedidoFecha;

}
