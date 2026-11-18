package org.gestionComercio.controller.caja;

import javafx.beans.Observable;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.dto.caja.MovimientoCajaDto;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.service.CajaService;
import org.gestionComercio.util.NumberUtils;

import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Ventana para registrar a mano un ingreso, egreso, gasto o retiro en la caja
 * abierta. Muestra cómo queda el saldo y avisa antes de registrar si un
 * egreso supera el efectivo disponible. Al aceptar llama a
 * {@link CajaService#registrarMovimiento}.
 */
class MovimientoCajaDialog extends FormularioCajaDialog<MovimientoCajaDto> {

    private final CajaService cajaService;
    private final BigDecimal saldoActual;

    private final ComboBox<TipoMovimientoCaja> cmbTipo = new ComboBox<>();
    private final TextField txtImporte = campoImporte(new TextField());
    private final Label lblSaldoResultante = valor("—");
    private final TextField txtDescripcion =
            campoTexto(new TextField(), "Ej.: pago de flete, cambio para la caja");

    MovimientoCajaDialog(CajaService cajaService, CajaDto caja) {
        this.cajaService = cajaService;
        this.saldoActual = caja.getSaldoEsperado();
    }

    /** Tipos que se pueden registrar a mano, en el orden en que se ofrecen. */
    static TipoMovimientoCaja[] tiposManuales() {
        return Arrays.stream(TipoMovimientoCaja.values())
                .filter(TipoMovimientoCaja::esManual)
                .toArray(TipoMovimientoCaja[]::new);
    }

    /**
     * Saldo que quedaría tras el movimiento.
     */
    static BigDecimal calcularSaldoResultante(BigDecimal saldoActual, TipoMovimientoCaja tipo, BigDecimal importe) {
        return saldoActual.add(importe.multiply(BigDecimal.valueOf(tipo.getSigno())));
    }

    /**
     * @return el mensaje de error si el egreso supera el saldo, o null si el movimiento es posible
     */
    static String validarSaldo(BigDecimal saldoActual, TipoMovimientoCaja tipo, BigDecimal importe) {

        if (tipo.esEgreso() && importe.compareTo(saldoActual) > 0) {
            return "El importe supera el saldo disponible en caja (" + CajaFormato.importe(saldoActual) + ").";
        }

        return null;
    }

    @Override
    protected String titulo() {
        return "Registrar movimiento de caja";
    }

    @Override
    protected String textoConfirmar() {
        return "Registrar";
    }

    @Override
    protected void armarFormulario() {

        cmbTipo.getItems().setAll(tiposManuales());
        cmbTipo.setValue(TipoMovimientoCaja.INGRESO_MANUAL);
        cmbTipo.setMaxWidth(Double.MAX_VALUE);

        fila("Tipo:", cmbTipo);
        fila("Saldo actual:", valor(CajaFormato.importe(saldoActual)));
        fila("Importe:", txtImporte);
        fila("Saldo resultante:", lblSaldoResultante);
        fila("Descripción:", txtDescripcion);
    }

    @Override
    protected Observable[] observados() {
        return new Observable[]{cmbTipo.valueProperty(), txtImporte.textProperty(), txtDescripcion.textProperty()};
    }

    @Override
    protected Node campoInicial() {
        return txtImporte;
    }

    @Override
    protected boolean actualizarEstado() {

        TipoMovimientoCaja tipo = cmbTipo.getValue();
        BigDecimal importe = leerImporte(txtImporte, "El importe", false);

        if (tipo == null || importe == null) {
            mostrarSaldoResultante(null);
            return false;
        }

        BigDecimal resultante = calcularSaldoResultante(saldoActual, tipo, importe);
        mostrarSaldoResultante(resultante);

        String error = validarSaldo(saldoActual, tipo, importe);

        if (error != null) {
            mostrarError(error);
            return false;
        }

        return !txtDescripcion.getText().isBlank();
    }

    @Override
    protected MovimientoCajaDto ejecutar() {
        return cajaService.registrarMovimiento(cmbTipo.getValue(),
                NumberUtils.parseDecimal(txtImporte.getText()), txtDescripcion.getText());
    }

    private void mostrarSaldoResultante(BigDecimal resultante) {

        if (resultante == null) {
            lblSaldoResultante.setText("—");
            lblSaldoResultante.setStyle("-fx-font-weight: bold;");
            return;
        }

        lblSaldoResultante.setText(CajaFormato.importe(resultante));
        lblSaldoResultante.setStyle(resultante.signum() < 0
                ? "-fx-font-weight: bold; -fx-text-fill: #c62828;"
                : "-fx-font-weight: bold;");
    }
}
