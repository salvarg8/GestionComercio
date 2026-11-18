package org.gestionComercio.controller.caja;

import javafx.beans.Observable;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.service.CajaService;
import org.gestionComercio.util.NumberUtils;

/**
 * Ventana para abrir la caja: el usuario indica el efectivo con el que
 * empieza. Al aceptar llama a {@link CajaService#abrir}.
 */
class AbrirCajaDialog extends FormularioCajaDialog<CajaDto> {

    private final CajaService cajaService;

    private final TextField txtSaldoInicial = campoImporte(new TextField());

    AbrirCajaDialog(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @Override
    protected String titulo() {
        return "Abrir caja";
    }

    @Override
    protected String textoConfirmar() {
        return "Abrir caja";
    }

    @Override
    protected void armarFormulario() {
        fila("Saldo inicial:", txtSaldoInicial);
    }

    @Override
    protected Observable[] observados() {
        return new Observable[]{txtSaldoInicial.textProperty()};
    }

    @Override
    protected Node campoInicial() {
        return txtSaldoInicial;
    }

    @Override
    protected boolean actualizarEstado() {
        return leerImporte(txtSaldoInicial, "El saldo inicial", true) != null;
    }

    @Override
    protected CajaDto ejecutar() {
        return cajaService.abrir(NumberUtils.parseDecimal(txtSaldoInicial.getText()));
    }
}
