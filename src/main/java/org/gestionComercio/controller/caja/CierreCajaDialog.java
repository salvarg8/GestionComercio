package org.gestionComercio.controller.caja;

import javafx.beans.Observable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.service.CajaService;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.util.NumberUtils;

import java.math.BigDecimal;

/**
 * Cierre de caja con arqueo: muestra el saldo esperado según el sistema, el
 * usuario carga el efectivo contado y ve la diferencia (sobrante o faltante)
 * antes de confirmar. Al aceptar llama a {@link CajaService#cerrar}.
 */
class CierreCajaDialog extends FormularioCajaDialog<CajaDto> {

    private final CajaService cajaService;
    private final CajaDto caja;

    private final TextField txtSaldoContado = campoImporte(new TextField());
    private final Label lblDiferencia = new Label("—");
    private final TextField txtObservacion =
            campoTexto(new TextField(), "Opcional; por ejemplo, para explicar una diferencia");

    CierreCajaDialog(CajaService cajaService, CajaDto caja) {
        this.cajaService = cajaService;
        this.caja = caja;
    }

    @Override
    protected String titulo() {
        return "Cerrar caja";
    }

    @Override
    protected String textoConfirmar() {
        return "Cerrar caja";
    }

    @Override
    protected void configurarBotonConfirmar(Button boton) {
        boton.getStyleClass().removeAll("btn-primary", "btn-secondary");
        boton.getStyleClass().add("btn-danger");
        boton.setDefaultButton(false);
    }

    @Override
    protected void armarFormulario() {

        fila("Apertura:", new Label(DateUtils.format(caja.getFechaApertura())));
        fila("Saldo inicial:", new Label(CajaFormato.importe(caja.getSaldoInicial())));
        fila("Ingresos:", new Label("+ " + CajaFormato.importe(caja.getTotalIngresos())));
        fila("Egresos:", new Label("− " + CajaFormato.importe(caja.getTotalEgresos())));
        fila("Saldo esperado:", valor(CajaFormato.importe(caja.getSaldoEsperado())));
        fila("Saldo contado:", txtSaldoContado);
        fila("Diferencia:", lblDiferencia);
        fila("Observación:", txtObservacion);
    }

    @Override
    protected Observable[] observados() {
        return new Observable[]{txtSaldoContado.textProperty()};
    }

    @Override
    protected Node campoInicial() {
        return txtSaldoContado;
    }

    @Override
    protected boolean actualizarEstado() {

        BigDecimal contado = leerImporte(txtSaldoContado, "El saldo contado", true);

        if (contado == null) {
            lblDiferencia.setText("—");
            lblDiferencia.getStyleClass().setAll("label");
            return false;
        }

        BigDecimal diferencia = calcularDiferencia(caja.getSaldoEsperado(), contado);

        lblDiferencia.setText(CajaFormato.textoDiferencia(diferencia));
        lblDiferencia.getStyleClass().setAll("label", "estado-badge", CajaFormato.estiloDiferencia(diferencia));

        return true;
    }

    /** Saldo contado − saldo esperado: positiva si sobra, negativa si falta. */
    static BigDecimal calcularDiferencia(BigDecimal esperado, BigDecimal contado) {
        return contado.subtract(esperado);
    }

    @Override
    protected CajaDto ejecutar() {
        return cajaService.cerrar(NumberUtils.parseDecimal(txtSaldoContado.getText()), txtObservacion.getText());
    }
}
