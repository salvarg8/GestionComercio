package org.gestionComercio.exception;

import org.springframework.stereotype.Component;

@Component
public class ErrorHandler {

    public void handle(Exception e) {

        if (e instanceof BusinessException) {

            ExceptionDialog.warning(e.getMessage());

            return;
        }

        ExceptionDialog.error(
                "Ocurrió un error inesperado.\n\n" +
                        "Si el problema persiste comuníquese con soporte.");

        e.printStackTrace();

    }

}