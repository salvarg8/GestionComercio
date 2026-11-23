package org.gestionComercio.controller.configuracion;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.base.ErroresPorCampo;
import org.gestionComercio.controller.venta.ImpresionComprobante;
import org.gestionComercio.dto.configuracion.ConfiguracionDto;
import org.gestionComercio.dto.configuracion.DatosInicialesResultado;
import org.gestionComercio.enums.ComprobantePorDefecto;
import org.gestionComercio.enums.FormaPago;
import org.gestionComercio.enums.FormatoTicket;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.enums.Redondeo;
import org.gestionComercio.enums.Rubro;
import org.gestionComercio.enums.UnidadMedida;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.impresion.ImpresoraLocal;
import org.gestionComercio.impresion.ImpresoraPdf;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.ComprobanteVentaService;
import org.gestionComercio.service.ConfiguracionService;
import org.gestionComercio.util.dialog.Dialogs;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Parámetros del sistema (compartidos por todas las PC), impresora de esta PC
 * y datos iniciales por rubro. Con CONFIGURACION_VER se consultan y se puede
 * imprimir una prueba; sin CONFIGURACION_EDITAR no se pueden cambiar.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class ConfiguracionSistemaController extends AbstractController {

    /** Opción de la lista de impresoras para abrir los comprobantes en el visor de PDF. */
    static final String VISOR_PDF = "Ninguna: abrir en el visor de PDF";

    private final ConfiguracionService configuracionService;
    private final ComprobanteVentaService comprobanteVentaService;
    private final ImpresionComprobante impresionComprobante;
    private final ImpresoraLocal impresoraLocal;
    private final ImpresoraPdf impresoraPdf;
    private final SesionUsuario sesionUsuario;

    @FXML
    private Label lblSoloLectura;

    @FXML
    private CheckBox chkVentaExigeCaja;

    @FXML
    private ComboBox<FormaPago> cmbFormaPago;

    @FXML
    private Label lblErrorFormaPago;

    @FXML
    private ComboBox<ComprobantePorDefecto> cmbComprobante;

    @FXML
    private ComboBox<Redondeo> cmbRedondeo;

    @FXML
    private CheckBox chkStockNegativo;

    @FXML
    private ComboBox<UnidadMedida> cmbUnidadMedida;

    @FXML
    private ComboBox<FormatoTicket> cmbFormatoTicket;

    @FXML
    private CheckBox chkImprimirLogo;

    @FXML
    private Label lblLogo;

    @FXML
    private TextArea txtPie;

    @FXML
    private Label lblErrorPie;

    @FXML
    private Label lblContadorPie;

    @FXML
    private ComboBox<String> cmbImpresora;

    @FXML
    private Button btnActualizarImpresoras;

    @FXML
    private Button btnProbar;

    @FXML
    private VBox boxDatosIniciales;

    @FXML
    private ComboBox<Rubro> cmbRubro;

    @FXML
    private Button btnCargarRubro;

    @FXML
    private Label lblRubro;

    @FXML
    private Button btnDescartar;

    @FXML
    private Button btnGuardar;

    private final ErroresPorCampo errores = new ErroresPorCampo();

    /** Valores tal como están guardados; null si no se pudieron cargar. */
    private ConfiguracionDto guardada;

    private boolean puedeEditar;

    @Override
    protected void initializeComponents() {

        cmbFormaPago.getItems().setAll(FormaPago.delPuntoDeVenta());
        cmbComprobante.getItems().setAll(ComprobantePorDefecto.values());
        cmbRedondeo.getItems().setAll(Redondeo.values());
        cmbUnidadMedida.getItems().setAll(UnidadMedida.values());
        cmbFormatoTicket.getItems().setAll(FormatoTicket.values());
        cmbRubro.getItems().setAll(Rubro.values());

        lblLogo.setText("El logo se elige en Datos de la empresa.");

        errores
                .registrar("formaPagoPorDefecto", cmbFormaPago, lblErrorFormaPago, cmbFormaPago.valueProperty())
                .registrar("pieComprobante", txtPie, lblErrorPie, txtPie.textProperty());

        aplicarPermisos();
        cargarImpresoras();

        execute(() -> cargar(configuracionService.obtener()));

        if (guardada == null) {
            btnGuardar.setDisable(true);
            btnDescartar.setDisable(true);
        }

        actualizarContadorPie();
        mostrarCategoriasDelRubro();
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnDescartar.setOnAction(e -> {
            if (guardada != null) {
                cargar(guardada);
                seleccionarImpresora(impresoraLocal.leer());
            }
        });

        txtPie.textProperty().addListener((obs, o, n) -> actualizarContadorPie());

        btnActualizarImpresoras.setOnAction(e -> cargarImpresoras());
        btnProbar.setOnAction(e -> imprimirPrueba());

        cmbRubro.valueProperty().addListener((obs, o, n) -> mostrarCategoriasDelRubro());
        btnCargarRubro.setOnAction(e -> cargarDatosIniciales());
    }

    /** Sin CONFIGURACION_EDITAR se ve todo pero no se puede cambiar (sí imprimir una prueba). */
    private void aplicarPermisos() {

        puedeEditar = sesionUsuario.tienePermiso(PermisoCodigo.CONFIGURACION_EDITAR);

        for (Node control : List.<Node>of(chkVentaExigeCaja, cmbFormaPago, cmbComprobante, cmbRedondeo,
                chkStockNegativo, cmbUnidadMedida, cmbFormatoTicket, chkImprimirLogo, cmbImpresora,
                btnActualizarImpresoras)) {
            control.setDisable(!puedeEditar);
        }

        txtPie.setEditable(puedeEditar);

        mostrar(lblSoloLectura, !puedeEditar);
        mostrar(boxDatosIniciales, puedeEditar);
        mostrar(btnGuardar, puedeEditar);
        mostrar(btnDescartar, puedeEditar);
    }

    private void cargar(ConfiguracionDto configuracion) {

        guardada = configuracion;

        chkVentaExigeCaja.setSelected(configuracion.exigeCaja());
        cmbFormaPago.setValue(configuracion.getFormaPagoPorDefecto());
        cmbComprobante.setValue(configuracion.getComprobantePorDefecto());
        cmbRedondeo.setValue(configuracion.getRedondeo());
        chkStockNegativo.setSelected(Boolean.TRUE.equals(configuracion.getStockNegativoPorDefecto()));
        cmbUnidadMedida.setValue(configuracion.getUnidadMedidaPorDefecto());
        cmbFormatoTicket.setValue(configuracion.getFormatoTicket());
        chkImprimirLogo.setSelected(!Boolean.FALSE.equals(configuracion.getImprimirLogo()));
        txtPie.setText(Objects.toString(configuracion.getPieComprobante(), ""));

        errores.limpiar();
    }

    /** Lo que muestra el formulario, todavía sin guardar. */
    private ConfiguracionDto formulario() {

        return ConfiguracionDto.builder()
                .ventaExigeCaja(chkVentaExigeCaja.isSelected())
                .formaPagoPorDefecto(cmbFormaPago.getValue())
                .comprobantePorDefecto(cmbComprobante.getValue())
                .redondeo(cmbRedondeo.getValue())
                .stockNegativoPorDefecto(chkStockNegativo.isSelected())
                .unidadMedidaPorDefecto(cmbUnidadMedida.getValue())
                .formatoTicket(cmbFormatoTicket.getValue())
                .imprimirLogo(chkImprimirLogo.isSelected())
                .pieComprobante(txtPie.getText())
                .build();
    }

    private void guardar() {

        errores.limpiar();

        execute(() -> {
            try {
                ConfiguracionDto actualizada = configuracionService.actualizar(formulario());
                impresoraLocal.guardar(impresoraElegida().orElse(null));

                cargar(actualizada);
                success("Configuración guardada. Rige desde la próxima operación.");

            } catch (ValidationException ex) {
                errores.mostrar(ex.getValidationResult());
            }
        });
    }

    private void actualizarContadorPie() {
        int largo = txtPie.getText() == null ? 0 : txtPie.getText().strip().length();
        lblContadorPie.setText(largo + " / " + ConfiguracionDto.PIE_MAX);
    }

    // ------------------------------------------------------------------
    // Impresora
    // ------------------------------------------------------------------

    /** Lista las impresoras instaladas y deja elegida la de esta PC (o la que estaba elegida en pantalla). */
    private void cargarImpresoras() {

        Optional<String> elegida = cmbImpresora.getValue() == null
                ? impresoraLocal.leer()
                : impresoraElegida();

        List<String> opciones = new ArrayList<>();
        opciones.add(VISOR_PDF);

        execute(() -> opciones.addAll(impresoraPdf.impresoras()));

        cmbImpresora.getItems().setAll(opciones);
        seleccionarImpresora(elegida);
    }

    /** Si la impresora guardada ya no está instalada, se muestra igual para que se vea cuál era. */
    private void seleccionarImpresora(Optional<String> impresora) {

        if (impresora.isEmpty()) {
            cmbImpresora.setValue(VISOR_PDF);
            return;
        }

        if (!cmbImpresora.getItems().contains(impresora.get())) {
            cmbImpresora.getItems().add(impresora.get());
        }

        cmbImpresora.setValue(impresora.get());
    }

    private Optional<String> impresoraElegida() {
        String valor = cmbImpresora.getValue();
        return valor == null || VISOR_PDF.equals(valor) ? Optional.empty() : Optional.of(valor);
    }

    /** Imprime un ticket de ejemplo con el formato y la impresora que muestra la pantalla. */
    private void imprimirPrueba() {

        execute(() -> {

            byte[] pdf = comprobanteVentaService.generarPrueba(formulario());
            Optional<String> impresora = impresoraElegida();

            impresionComprobante.enviar(pdf, "Prueba-de-impresion", impresora);

            if (impresora.isPresent()) {
                info("Se envió la prueba a \"" + impresora.get() + "\".");
            }
        });
    }

    // ------------------------------------------------------------------
    // Datos iniciales
    // ------------------------------------------------------------------

    private void mostrarCategoriasDelRubro() {

        Rubro rubro = cmbRubro.getValue();

        lblRubro.setText(rubro == null ? "" : "Categorías: " + String.join(", ", rubro.getCategorias())
                + ". Unidad de medida por defecto: " + rubro.getUnidadPorDefecto().getDescripcion() + ".");
        btnCargarRubro.setDisable(rubro == null);
    }

    private void cargarDatosIniciales() {

        Rubro rubro = cmbRubro.getValue();

        if (rubro == null || !confirm("Datos iniciales",
                "¿Cargar las categorías típicas de \"" + rubro + "\" y usar "
                        + rubro.getUnidadPorDefecto().getDescripcion().toLowerCase()
                        + " como unidad por defecto de los productos nuevos?")) {
            return;
        }

        execute(() -> {

            DatosInicialesResultado resultado = configuracionService.cargarDatosIniciales(rubro);

            List<String> detalle = new ArrayList<>();
            resultado.creadas().forEach(c -> detalle.add("Creada: " + c));
            resultado.existentes().forEach(c -> detalle.add("Ya existía: " + c));

            Dialogs.lista(Dialogs.Tipo.EXITO, "Datos iniciales cargados",
                    "Se cargaron los datos de " + rubro + ". La unidad por defecto de los productos nuevos es "
                            + rubro.getUnidadPorDefecto().getDescripcion().toLowerCase() + ".",
                    detalle);

            // La unidad por defecto cambió en la base: se refleja sin perder lo demás que se esté editando.
            cmbUnidadMedida.setValue(rubro.getUnidadPorDefecto());
            if (guardada != null) {
                guardada.setUnidadMedidaPorDefecto(rubro.getUnidadPorDefecto());
            }
        });
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }
}
