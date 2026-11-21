package org.gestionComercio.controller.caja;

import javafx.beans.Observable;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.dto.caja.ResponsableCajaDto;
import org.gestionComercio.service.CajaService;
import org.gestionComercio.util.NumberUtils;

import java.util.List;
import java.util.Objects;

/**
 * Ventana para abrir la caja: el usuario indica el efectivo con el que
 * empieza y, si puede abrir cajas de otros usuarios, para quién la abre.
 * Al aceptar llama a {@link CajaService#abrir(Long, java.math.BigDecimal)}.
 */
class AbrirCajaDialog extends FormularioCajaDialog<CajaDto> {

    private final CajaService cajaService;

    /** Null si el usuario sólo puede abrir su propia caja. */
    private final ComboBox<ResponsableCajaDto> cmbResponsable;

    private final TextField txtSaldoInicial = campoImporte(new TextField());

    /** Para abrir la caja propia, sin selector de responsable. */
    AbrirCajaDialog(CajaService cajaService) {
        this(cajaService, null, null);
    }

    /**
     * @param responsables       usuarios a los que se puede abrir la caja; null para no mostrar el selector
     * @param responsableInicial id del usuario elegido al abrir la ventana; null para no elegir ninguno
     */
    AbrirCajaDialog(CajaService cajaService, List<ResponsableCajaDto> responsables, Long responsableInicial) {

        this.cajaService = cajaService;

        if (responsables == null) {
            cmbResponsable = null;
            return;
        }

        cmbResponsable = new ComboBox<>();
        cmbResponsable.getItems().setAll(responsables);
        cmbResponsable.setPromptText("Seleccione el usuario");
        cmbResponsable.setMaxWidth(Double.MAX_VALUE);

        responsables.stream()
                .filter(r -> Objects.equals(r.id(), responsableInicial))
                .findFirst()
                .ifPresent(cmbResponsable::setValue);
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

        if (cmbResponsable != null) {
            fila("Responsable:", cmbResponsable);
        }

        fila("Saldo inicial:", txtSaldoInicial);
    }

    @Override
    protected Observable[] observados() {

        return cmbResponsable == null
                ? new Observable[]{txtSaldoInicial.textProperty()}
                : new Observable[]{txtSaldoInicial.textProperty(), cmbResponsable.valueProperty()};
    }

    @Override
    protected Node campoInicial() {
        return cmbResponsable != null && cmbResponsable.getValue() == null ? cmbResponsable : txtSaldoInicial;
    }

    @Override
    protected boolean actualizarEstado() {

        boolean importeValido = leerImporte(txtSaldoInicial, "El saldo inicial", true) != null;

        return importeValido && (cmbResponsable == null || cmbResponsable.getValue() != null);
    }

    @Override
    protected CajaDto ejecutar() {

        Long responsableId = cmbResponsable == null ? null : cmbResponsable.getValue().id();

        return cajaService.abrir(responsableId, NumberUtils.parseDecimal(txtSaldoInicial.getText()));
    }
}
