package org.gestionComercio.service;

import org.gestionComercio.licencia.EstadoLicencia;

public interface LicenciaService {

    /** Estado de la licencia en esta PC. No requiere permisos. */
    EstadoLicencia estado();

    /**
     * Registra que se usó el sistema hoy (para detectar un reloj atrasado) y
     * devuelve el estado. Se llama al iniciar sesión.
     */
    EstadoLicencia verificarAlIniciarSesion();

    /**
     * Activa una licencia para todo el comercio. Reemplaza a la anterior.
     * Requiere LICENCIA_ADMINISTRAR.
     *
     * @throws org.gestionComercio.exception.BusinessException si la licencia es inválida, no incluye esta
     *                                                         PC, venció o es anterior a la activa
     */
    EstadoLicencia activar(String clave);

    /** Código de esta PC, para pedir una licencia que la incluya. No requiere permisos. */
    String codigoEquipo();

}
