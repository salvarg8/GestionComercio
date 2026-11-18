package org.gestionComercio.controller.caja;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.dto.caja.MovimientoCajaDto;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.TipoMovimientoCaja;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.CajaService;
import org.gestionComercio.util.DateUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Optional;

/**
 * Caja del usuario de la sesión. Si no tiene una abierta lo informa y, con
 * CAJA_ABRIR, permite abrirla ({@link AbrirCajaDialog}). Con la caja abierta
 * muestra los totales, el saldo actual y los movimientos, y según los
 * permisos permite registrar ingresos y egresos manuales
 * ({@link MovimientoCajaDialog}) y cerrarla con arqueo
 * ({@link CierreCajaDialog}).
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class CajaController extends AbstractController {

    @FXML
    private Label lblEstado;

    @FXML
    private Button btnHistorial;

    @FXML
    private VBox panelSinCaja;

    @FXML
    private Button btnAbrir;

    @FXML
    private Label lblSinPermisoAbrir;

    @FXML
    private VBox panelCajaAbierta;

    @FXML
    private Label lblApertura;

    @FXML
    private Label lblSaldoInicial;

    @FXML
    private Label lblIngresos;

    @FXML
    private Label lblEgresos;

    @FXML
    private Label lblSaldoActual;

    @FXML
    private Button btnRegistrarMovimiento;

    @FXML
    private Button btnCerrar;

    @FXML
    private TableView<MovimientoCajaDto> tblMovimientos;

    @FXML
    private TableColumn<MovimientoCajaDto, String> colFecha;

    @FXML
    private TableColumn<MovimientoCajaDto, TipoMovimientoCaja> colTipo;

    @FXML
    private TableColumn<MovimientoCajaDto, String> colDescripcion;

    @FXML
    private TableColumn<MovimientoCajaDto, String> colImporte;

    @FXML
    private TableColumn<MovimientoCajaDto, String> colUsuario;

    @FXML
    private Label lblTotal;

    private final CajaService cajaService;
    private final SesionUsuario sesionUsuario;
    private final Navigator navigator;

    /** Caja abierta que se está mostrando; null si no hay. */
    private CajaDto caja;

    @Override
    protected void initializeComponents() {

        CajaFormato.configurarColumnasMovimientos(colFecha, colTipo, colDescripcion, colImporte, colUsuario);

        boolean puedeAbrir = sesionUsuario.tienePermiso(PermisoCodigo.CAJA_ABRIR);
        mostrar(btnAbrir, puedeAbrir);
        mostrar(lblSinPermisoAbrir, !puedeAbrir);

        mostrar(btnRegistrarMovimiento, sesionUsuario.tienePermiso(PermisoCodigo.CAJA_REGISTRAR_MOVIMIENTO));
        mostrar(btnCerrar, sesionUsuario.tienePermiso(PermisoCodigo.CAJA_CERRAR));
        mostrar(btnHistorial, navigator.puedeAcceder(AppView.CAJA_HISTORIAL));

        cargar();
    }

    @Override
    protected void initializeEvents() {

        btnAbrir.setOnAction(e -> abrir());
        btnRegistrarMovimiento.setOnAction(e -> registrarMovimiento());
        btnCerrar.setOnAction(e -> cerrar());
        btnHistorial.setOnAction(e -> navigator.navigate(AppView.CAJA_HISTORIAL));
    }

    private void abrir() {

        new AbrirCajaDialog(cajaService).mostrar().ifPresent(abierta -> {

            cargar();

            success("Se abrió la caja con un saldo inicial de "
                    + CajaFormato.importe(abierta.getSaldoInicial()) + ".");
        });
    }

    private void registrarMovimiento() {

        if (caja == null) {
            return;
        }

        new MovimientoCajaDialog(cajaService, caja).mostrar().ifPresent(m -> {

            cargar();

            success("Se registró el movimiento.\n\n"
                    + "Tipo: " + m.getTipo().getDescripcion() + "\n"
                    + "Importe: " + CajaFormato.importeConSigno(m.getTipo(), m.getImporte()) + "\n"
                    + "Descripción: " + m.getDescripcion() + "\n"
                    + "Saldo actual: " + CajaFormato.importe(caja == null ? null : caja.getSaldoEsperado()));
        });
    }

    private void cerrar() {

        if (caja == null) {
            return;
        }

        // Se recarga antes para arquear contra el saldo más reciente.
        cargar();

        if (caja == null) {
            return;
        }

        Optional<CajaDto> cerrada = new CierreCajaDialog(cajaService, caja).mostrar();

        cerrada.ifPresent(c -> {

            cargar();

            success("Se cerró la caja.\n\n"
                    + "Saldo esperado: " + CajaFormato.importe(c.getSaldoEsperado()) + "\n"
                    + "Saldo contado: " + CajaFormato.importe(c.getSaldoFinal()) + "\n"
                    + "Diferencia: " + CajaFormato.textoDiferencia(c.getDiferencia()));
        });
    }

    /** Consulta la caja abierta del usuario y muestra el panel que corresponde. */
    private void cargar() {

        execute(() -> {

            caja = cajaService.obtenerCajaAbierta().orElse(null);

            boolean abierta = caja != null;
            mostrar(panelSinCaja, !abierta);
            mostrar(panelCajaAbierta, abierta);

            lblEstado.setText(abierta ? "Abierta" : "Sin caja abierta");
            lblEstado.getStyleClass().setAll("label", "estado-badge", abierta ? "estado-activo" : "estado-inactivo");

            if (!abierta) {
                tblMovimientos.getItems().clear();
                lblTotal.setText("");
                return;
            }

            lblApertura.setText("Abierta el " + DateUtils.format(caja.getFechaApertura())
                    + " por " + CajaFormato.usuario(caja.getUsuarioNombreCompleto(), caja.getUsuarioNombre()));

            lblSaldoInicial.setText(CajaFormato.importe(caja.getSaldoInicial()));
            lblIngresos.setText("+ " + CajaFormato.importe(caja.getTotalIngresos()));
            lblEgresos.setText("− " + CajaFormato.importe(caja.getTotalEgresos()));
            lblSaldoActual.setText(CajaFormato.importe(caja.getSaldoEsperado()));

            List<MovimientoCajaDto> movimientos = cajaService.listarMovimientos(caja.getId());
            tblMovimientos.getItems().setAll(movimientos);

            lblTotal.setText("Total: " + movimientos.size()
                    + (movimientos.size() == 1 ? " movimiento" : " movimientos"));
        });
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }
}
