package org.gestionComercio.controller.base;

import org.gestionComercio.util.AlertUtils;

public abstract class AbstractController {

    protected void info(String mensaje){

        AlertUtils.information("Información", mensaje);

    }

    protected void warning(String mensaje){

        AlertUtils.warning("Atención", mensaje);

    }

    protected void error(String mensaje){

        AlertUtils.error("Error", mensaje);

    }

}