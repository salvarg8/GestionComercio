package org.gestionComercio.controller.facturacion;

import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.facturacion.FacturaElectronicaFormato.Resultado;
import org.gestionComercio.dto.arca.ComprobanteArcaDto;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.service.FacturacionElectronicaService;
import org.gestionComercio.util.dialog.Dialogs;
import org.gestionComercio.view.component.TareaEnSegundoPlano;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Pide en segundo plano el CAE de los comprobantes de una venta (después de
 * registrarla o anularla, o para reintentar), con una ventana de espera
 * mientras ARCA responde.
 */
@Component
@RequiredArgsConstructor
public class AutorizacionComprobantes {

    private final FacturacionElectronicaService facturacion;

    /**
     * Pide el CAE y entrega el resultado sin mostrarlo, para que la pantalla lo
     * combine con su propio mensaje.
     *
     * @param alTerminar recibe los comprobantes de la venta (vacío si no se pudo enviar
     *                   el pedido) y el resumen para el usuario
     */
    public void autorizar(Long ventaId, BiConsumer<List<ComprobanteArcaDto>, Resultado> alTerminar) {

        TareaEnSegundoPlano.ejecutar("Pidiendo el CAE a ARCA…",
                () -> facturacion.autorizar(ventaId),
                comprobantes -> alTerminar.accept(comprobantes, FacturaElectronicaFormato.resultado(comprobantes)),
                error -> alTerminar.accept(List.of(), noEnviado(error)));
    }

    /** Pide el CAE y muestra el resultado; después llama a {@code alTerminar}. */
    public void autorizarYMostrar(Long ventaId, Runnable alTerminar) {

        autorizar(ventaId, (comprobantes, resultado) -> {
            mostrar(resultado);
            alTerminar.run();
        });
    }

    public static void mostrar(Resultado resultado) {

        switch (resultado.tipo()) {
            case EXITO -> Dialogs.exito(resultado.titulo(), resultado.mensaje());
            case ERROR -> Dialogs.error(resultado.titulo(), resultado.mensaje());
            case INFORMACION -> Dialogs.informacion(resultado.titulo(), resultado.mensaje());
            default -> Dialogs.advertencia(resultado.titulo(), resultado.mensaje());
        }
    }

    private static Resultado noEnviado(Throwable error) {

        if (error instanceof BusinessException) {
            return new Resultado(Dialogs.Tipo.ADVERTENCIA, "Comprobante pendiente de CAE",
                    FacturaElectronicaFormato.noEnviado(error.getMessage()), false);
        }

        return new Resultado(Dialogs.Tipo.ERROR, "Comprobante pendiente de CAE",
                FacturaElectronicaFormato.noEnviado("ocurrió un error inesperado. Si el problema persiste, "
                        + "comuníquese con soporte."), false);
    }

}
