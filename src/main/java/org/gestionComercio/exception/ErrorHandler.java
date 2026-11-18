package org.gestionComercio.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
public class ErrorHandler {

    public void handle(Exception e) {

        if (e instanceof BusinessException) {

            ExceptionDialog.warning(e.getMessage());

            return;
        }

        // Conflicto de @Version detectado al confirmar la transacción, fuera del servicio.
        if (e instanceof OptimisticLockingFailureException) {

            ExceptionDialog.warning(
                    "Otro usuario u operación modificó estos datos mientras trabajaba. "
                            + "Vuelva a abrirlos e intente nuevamente.");

            return;
        }

        ExceptionDialog.error(
                "Ocurrió un error inesperado.\n\n" +
                        "Si el problema persiste comuníquese con soporte.");

        e.printStackTrace();

    }

}