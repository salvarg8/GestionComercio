package org.gestionComercio.controller.caja;

import javafx.beans.Observable;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
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

    /** Si los retiros los tiene que confirmar un encargado con su usuario y contraseña. */
    private final boolean pedirEncargado;

    private final TextField txtEncargado = new TextField();
    private final PasswordField txtPasswordEncargado = new PasswordField();

    /**
     * @param pedirEncargado true si los retiros requieren confirmación y el
     *                       usuario de la sesión no puede confirmarlos: se
     *                       piden el usuario y la contraseña de un encargado
     */
    MovimientoCajaDialog(CajaService cajaService, CajaDto caja, boolean pedirEncargado) {
        this.cajaService = cajaService;
        this.saldoActual = caja.getSaldoEsperado();
        this.pedirEncargado = pedirEncargado;
    }

    /** Si el movimiento elegido necesita las credenciales del encargado. */
    static boolean requiereEncargado(boolean pedirEncargado, TipoMovimientoCaja tipo) {
        return pedirEncargado && tipo == TipoMovimientoCaja.RETIRO;
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

        if (pedirEncargado) {

            Label aviso = new Label("Los retiros los confirma un encargado: ingrese su usuario y contraseña.");
            aviso.getStyleClass().add("form-hint");
            aviso.setWrapText(true);
            aviso.setMaxWidth(320);

            txtEncargado.setPromptText("Usuario del encargado");
            txtEncargado.setMaxWidth(Double.MAX_VALUE);
            txtPasswordEncargado.setPromptText("Contraseña");
            txtPasswordEncargado.setMaxWidth(Double.MAX_VALUE);

            fila("Confirma:", aviso);
            fila("Usuario:", txtEncargado);
            fila("Contraseña:", txtPasswordEncargado);
        }
    }

    @Override
    protected Observable[] observados() {
        return new Observable[]{cmbTipo.valueProperty(), txtImporte.textProperty(), txtDescripcion.textProperty(),
                txtEncargado.textProperty(), txtPasswordEncargado.textProperty()};
    }

    @Override
    protected Node campoInicial() {
        return txtImporte;
    }

    @Override
    protected boolean actualizarEstado() {

        TipoMovimientoCaja tipo = cmbTipo.getValue();

        // Las credenciales del encargado sólo se usan en los retiros.
        boolean conEncargado = requiereEncargado(pedirEncargado, tipo);
        txtEncargado.setDisable(!conEncargado);
        txtPasswordEncargado.setDisable(!conEncargado);

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

        if (txtDescripcion.getText().isBlank()) {
            return false;
        }

        return !conEncargado || (!txtEncargado.getText().isBlank() && !txtPasswordEncargado.getText().isEmpty());
    }

    @Override
    protected MovimientoCajaDto ejecutar() {

        boolean conEncargado = requiereEncargado(pedirEncargado, cmbTipo.getValue());

        try {
            return cajaService.registrarMovimiento(cmbTipo.getValue(),
                    NumberUtils.parseDecimal(txtImporte.getText()), txtDescripcion.getText(),
                    conEncargado ? txtEncargado.getText() : null,
                    conEncargado ? txtPasswordEncargado.getText() : null);
        } finally {
            // La contraseña no queda escrita: si se rechaza, se vuelve a ingresar.
            txtPasswordEncargado.clear();
        }
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
