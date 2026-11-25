package org.gestionComercio.controller.facturacion;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.arca.ConfiguracionArcaDto;
import org.gestionComercio.dto.arca.PruebaConexionArca;
import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.enums.AmbienteArca;
import org.gestionComercio.enums.OrigenClaveArca;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.service.ConfiguracionArcaService;
import org.gestionComercio.service.EmpresaService;
import org.gestionComercio.util.dialog.Dialogs;
import org.gestionComercio.view.component.TareaEnSegundoPlano;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Configuración de la facturación electrónica con una guía paso a paso:
 * datos de la empresa, ambiente, punto de venta, pedido e importación del
 * certificado, autorización en ARCA, prueba de conexión y habilitación.
 * Cada paso muestra si ya está hecho. Requiere FACTURACION_CONFIGURAR.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class FacturacionConfigController extends AbstractController {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String HECHO = "paso-hecho";

    @FXML
    private Button btnActualizar;

    @FXML
    private VBox panelAvisos;

    @FXML
    private HBox boxEstado;

    @FXML
    private Label lblAmbiente;

    @FXML
    private Label lblPuntoVenta;

    @FXML
    private Label lblCertificado;

    @FXML
    private VBox contenedorPasos;

    private final ConfiguracionArcaService configuracionArca;
    private final EmpresaService empresaService;
    private final Navigator navigator;
    private final HostServices hostServices;

    // Pasos de la guía
    private Paso pasoEmpresa;
    private Paso pasoClave;
    private Paso pasoAmbiente;
    private Paso pasoPuntoVenta;
    private Paso pasoPedido;
    private Paso pasoObtener;
    private Paso pasoImportar;
    private Paso pasoAutorizar;
    private Paso pasoProbar;
    private Paso pasoHabilitar;

    private final ComboBox<AmbienteArca> cmbAmbiente = new ComboBox<>();
    private final TextField txtPuntoVenta = new TextField();
    private final Label lblEmpresa = texto("");
    private final Label lblInstruccionesPuntoVenta = texto("");
    private final Label lblInstruccionesObtener = texto("");
    private final Label lblInstruccionesAutorizar = texto("");
    private final VBox resultadoPrueba = new VBox(6);
    private final Button btnGenerarPedido = boton("Generar y guardar el pedido (.csr)…", "btn-primary");
    private final Button btnImportarCertificado = boton("Importar certificado (.crt)…", "btn-primary");
    private final Button btnImportarPkcs12 = boton("Importar .p12 / .pfx…", "btn-secondary");
    private final Button btnProbar = boton("Probar conexión con ARCA", "btn-primary");
    private final Button btnHabilitar = boton("", "btn-primary");
    private final Label lblClave = texto("");
    private final PasswordField txtClave = new PasswordField();
    private final PasswordField txtClaveRepetida = new PasswordField();
    private final VBox camposClave = new VBox(8);
    private final Button btnGuardarClave = boton("Guardar la clave en esta PC", "btn-primary");
    private final Button btnCambiarClave = boton("Cambiar la clave…", "btn-secondary");

    /** Configuración tal como está guardada; null si no se pudo cargar. */
    private ConfiguracionArcaDto configuracion;

    /** Resultado de la última prueba de conexión hecha en esta pantalla; null si no se probó. */
    private PruebaConexionArca ultimaPrueba;

    @Override
    protected void initializeComponents() {

        cmbAmbiente.getItems().setAll(AmbienteArca.values());
        cmbAmbiente.setPrefWidth(240);

        txtPuntoVenta.setPrefColumnCount(6);
        txtPuntoVenta.setPromptText("Ej.: 3");
        txtPuntoVenta.setTextFormatter(new TextFormatter<String>(cambio ->
                cambio.getControlNewText().matches("\\d{0,5}") ? cambio : null));

        armarPasos();

        cargar();
    }

    @Override
    protected void initializeEvents() {

        btnActualizar.setOnAction(e -> cargar());

        cmbAmbiente.valueProperty().addListener((obs, o, n) -> actualizarInstrucciones());

        btnGenerarPedido.setOnAction(e -> generarPedido());
        btnImportarCertificado.setOnAction(e -> importarCertificado());
        btnImportarPkcs12.setOnAction(e -> importarPkcs12());
        btnProbar.setOnAction(e -> probarConexion());
        btnHabilitar.setOnAction(e -> cambiarHabilitacion());
        btnGuardarClave.setOnAction(e -> guardarClave());
        btnCambiarClave.setOnAction(e -> cambiarClave());
    }

    // ------------------------------------------------------------------
    // Armado de la guía
    // ------------------------------------------------------------------

    private void armarPasos() {

        Button btnIrEmpresa = boton("Ir a los datos de la empresa", "btn-secondary");
        btnIrEmpresa.setOnAction(e -> navigator.navigate(AppView.EMPRESA_CONFIG));
        mostrar(btnIrEmpresa, navigator.puedeAcceder(AppView.EMPRESA_CONFIG));

        pasoEmpresa = new Paso(1, "Datos de la empresa",
                texto("Las facturas usan el CUIT, la razón social, la condición frente al IVA y la fecha de inicio "
                        + "de actividades de la empresa."),
                lblEmpresa, acciones(btnIrEmpresa));

        txtClave.setPromptText("Clave");
        txtClave.setPrefColumnCount(22);
        txtClaveRepetida.setPromptText("Repita la clave");
        txtClaveRepetida.setPrefColumnCount(22);
        txtClave.setOnAction(e -> guardarClave());
        txtClaveRepetida.setOnAction(e -> guardarClave());
        camposClave.getChildren().setAll(acciones(txtClave, txtClaveRepetida, btnGuardarClave));

        pasoClave = new Paso(2, "Clave del certificado en esta PC",
                texto(GuiaArca.clave()),
                lblClave, camposClave, acciones(btnCambiarClave));

        Button btnGuardarAmbiente = boton("Guardar ambiente", "btn-secondary");
        btnGuardarAmbiente.setOnAction(e -> guardar());

        pasoAmbiente = new Paso(3, "Ambiente",
                texto(GuiaArca.ambiente()),
                acciones(new Label("Ambiente:"), cmbAmbiente, btnGuardarAmbiente));

        Button btnArcaPuntoVenta = enlaceArca();
        Button btnGuardarPuntoVenta = boton("Guardar punto de venta", "btn-secondary");
        btnGuardarPuntoVenta.setOnAction(e -> guardar());

        pasoPuntoVenta = new Paso(4, "Punto de venta para factura electrónica",
                lblInstruccionesPuntoVenta,
                acciones(btnArcaPuntoVenta, new Label("Número de punto de venta:"), txtPuntoVenta,
                        btnGuardarPuntoVenta));

        pasoPedido = new Paso(5, "Generar el pedido de certificado",
                texto(GuiaArca.generarPedido()),
                acciones(btnGenerarPedido));

        pasoObtener = new Paso(6, "Obtener el certificado en ARCA",
                lblInstruccionesObtener,
                acciones(enlaceArca()));

        pasoImportar = new Paso(7, "Importar el certificado",
                texto(GuiaArca.importarCertificado()),
                acciones(btnImportarCertificado, btnImportarPkcs12));

        pasoAutorizar = new Paso(8, "Autorizar el certificado para facturar",
                lblInstruccionesAutorizar,
                acciones(enlaceArca()));

        pasoProbar = new Paso(9, "Probar la conexión",
                texto(GuiaArca.probarConexion()),
                acciones(btnProbar),
                resultadoPrueba);

        pasoHabilitar = new Paso(10, "Habilitar la facturación electrónica",
                texto(GuiaArca.habilitar()),
                acciones(btnHabilitar));

        // Los pasos que se hacen en ARCA no se pueden verificar desde aquí.
        pasoObtener.estado("Se hace en el sitio de ARCA", false);
        pasoAutorizar.estado("Se hace en el sitio de ARCA (se verifica al probar la conexión)", false);

        contenedorPasos.getChildren().setAll(Arrays.stream(new Paso[]{pasoEmpresa, pasoClave, pasoAmbiente, pasoPuntoVenta,
                pasoPedido, pasoObtener, pasoImportar, pasoAutorizar, pasoProbar, pasoHabilitar})
                .map(Paso::caja)
                .toList());
    }

    private void actualizarInstrucciones() {

        AmbienteArca ambiente = cmbAmbiente.getValue() == null ? AmbienteArca.HOMOLOGACION : cmbAmbiente.getValue();

        lblInstruccionesPuntoVenta.setText(GuiaArca.puntoDeVenta(ambiente));
        lblInstruccionesObtener.setText(GuiaArca.obtenerCertificado(ambiente));
        lblInstruccionesAutorizar.setText(GuiaArca.autorizarCertificado(ambiente));
    }

    // ------------------------------------------------------------------
    // Carga y estado
    // ------------------------------------------------------------------

    private void cargar() {

        execute(() -> mostrarConfiguracion(configuracionArca.obtener()));
        mostrarEmpresa();
    }

    private void mostrarConfiguracion(ConfiguracionArcaDto config) {

        configuracion = config;

        cmbAmbiente.setValue(config.getAmbiente());
        txtPuntoVenta.setText(config.getPuntoVenta() == null ? "" : String.valueOf(config.getPuntoVenta()));
        actualizarInstrucciones();

        // Estado actual
        Label estado = new Label(config.isHabilitada() ? "Habilitada" : "Deshabilitada");
        estado.getStyleClass().setAll("label", "estado-badge", config.isHabilitada() ? "estado-activo" : "estado-inactivo");
        boxEstado.getChildren().setAll(estado);

        lblAmbiente.setText(config.getAmbiente().getDescripcion());
        lblPuntoVenta.setText(config.getPuntoVenta() == null ? "Sin indicar" : String.valueOf(config.getPuntoVenta()));
        lblCertificado.setText(descripcionCertificado(config));

        mostrarAvisos(config);

        // Pasos
        pasoAmbiente.estado(config.getAmbiente().getDescripcion(), true);

        pasoPuntoVenta.estado(config.getPuntoVenta() == null ? "Pendiente"
                : "Punto de venta " + config.getPuntoVenta(), config.getPuntoVenta() != null);

        if (config.isPedidoPendiente()) {
            pasoPedido.estado("Pedido generado" + (config.getPedidoFecha() == null ? ""
                    : " el " + config.getPedidoFecha().format(FORMATO_FECHA)) + ": falta importar el certificado", true);
        } else {
            pasoPedido.estado(config.isCertificadoCargado() ? "Hecho (certificado importado)" : "Pendiente",
                    config.isCertificadoCargado());
        }

        if (config.isCertificadoCargado()) {
            pasoImportar.estado(config.getDiasParaVencer() != null && config.getDiasParaVencer() < 0
                    ? "El certificado venció" : "Certificado importado", config.getDiasParaVencer() == null
                    || config.getDiasParaVencer() >= 0);
        } else {
            pasoImportar.estado("Pendiente", false);
        }

        mostrarPrueba();

        pasoHabilitar.estado(config.isHabilitada() ? "Habilitada" : "Deshabilitada", config.isHabilitada());
        btnHabilitar.setText(config.isHabilitada() ? "Deshabilitar la facturación electrónica"
                : "Habilitar la facturación electrónica");
        btnHabilitar.getStyleClass().removeAll("btn-primary", "btn-danger");
        btnHabilitar.getStyleClass().add(config.isHabilitada() ? "btn-danger" : "btn-primary");

        mostrarClave(config);

        // Sin la clave del certificado no se puede generar, importar ni habilitar. Cada paso dice qué falta.
        boolean clave = claveUtilizable(config);
        String sinClave = "Disponible cuando esta PC tenga la clave del certificado (paso 2).";

        btnGenerarPedido.setDisable(!clave);
        pasoPedido.motivo(clave ? null : sinClave);

        btnImportarCertificado.setDisable(!clave || !config.isPedidoPendiente());
        btnImportarPkcs12.setDisable(!clave);
        pasoImportar.motivo(!clave ? sinClave : !config.isPedidoPendiente()
                ? "Para importar el .crt primero genere el pedido de certificado (paso 5). Un .p12 o .pfx se "
                + "puede importar igual." : null);

        btnProbar.setDisable(!clave || !config.isCertificadoCargado());
        pasoProbar.motivo(!clave ? sinClave : !config.isCertificadoCargado()
                ? "Disponible cuando haya un certificado importado (paso 7)." : null);

        btnHabilitar.setDisable(!clave && !config.isHabilitada());
        pasoHabilitar.motivo(!clave && !config.isHabilitada() ? sinClave : null);
    }

    /** @return true si esta PC tiene la clave y abre lo guardado (o todavía no hay nada guardado). */
    private static boolean claveUtilizable(ConfiguracionArcaDto config) {
        return config.isClaveConfigurada() && !Boolean.FALSE.equals(config.getClaveCorrecta());
    }

    private static boolean hayCertificadoGuardado(ConfiguracionArcaDto config) {
        return config.isCertificadoCargado() || config.isPedidoPendiente();
    }

    private void mostrarClave(ConfiguracionArcaDto config) {

        boolean utilizable = claveUtilizable(config);
        boolean variable = config.getOrigenClave() == OrigenClaveArca.VARIABLE_ENTORNO;
        boolean primeraVez = !hayCertificadoGuardado(config);

        if (variable) {
            lblClave.setText(utilizable
                    ? "Esta PC toma la clave de la variable de entorno ARCA_CLAVE_CERTIFICADO."
                    : "La variable de entorno ARCA_CLAVE_CERTIFICADO de esta PC no abre el certificado guardado. "
                    + "Corríjala con la clave que se usa en las otras PC.");
        } else if (!config.isClaveConfigurada()) {
            lblClave.setText(primeraVez ? GuiaArca.clavePrimeraVez() : GuiaArca.claveOtraPc());
        } else if (!utilizable) {
            lblClave.setText("La clave guardada en esta PC no abre el certificado guardado en el sistema. "
                    + GuiaArca.claveOtraPc());
        } else {
            lblClave.setText("La clave está guardada en esta PC.");
        }

        pasoClave.estado(utilizable ? config.getOrigenClave().getDescripcion() : "Pendiente", utilizable);

        // Los campos se muestran cuando falta la clave; con la variable de entorno no se usan.
        boolean pedir = !variable && !utilizable;
        mostrar(camposClave, pedir);
        mostrar(btnCambiarClave, !variable && utilizable);
        mostrar(txtClaveRepetida, primeraVez);
        txtClave.clear();
        txtClaveRepetida.clear();
    }

    private void guardarClave() {

        if (configuracion == null) {
            return;
        }

        String clave = txtClave.getText();

        if (txtClaveRepetida.isVisible() && !clave.equals(txtClaveRepetida.getText())) {
            warning("Las dos claves no coinciden. Escríbalas de nuevo.");
            txtClaveRepetida.clear();
            txtClaveRepetida.requestFocus();
            return;
        }

        try {

            mostrarConfiguracion(configuracionArca.guardarClaveEnEstaPc(clave.toCharArray()));
            success("Clave guardada en esta PC.");

        } catch (BusinessException ex) {

            if (hayCertificadoGuardado(configuracion) && ex.getMessage().contains("no corresponde")) {
                ofrecerDescartarCertificado(ex.getMessage(), clave);
            } else {
                warning(ex.getMessage());
            }

        } catch (RuntimeException ex) {
            TareaEnSegundoPlano.mostrarError(ex);
        }
    }

    /**
     * Cambia la clave: el certificado se vuelve a cifrar con la nueva y queda
     * guardada en esta PC. Las demás PC tienen que escribirla una vez.
     */
    private void cambiarClave() {

        PasswordField nueva = new PasswordField();
        nueva.setPromptText("Clave nueva (al menos 8 caracteres)");
        PasswordField repetida = new PasswordField();
        repetida.setPromptText("Repita la clave nueva");

        ButtonType cambiar = new ButtonType("Cambiar la clave", ButtonData.OK_DONE);

        var alert = Dialogs.crear(Dialogs.Tipo.CONFIRMACION, "Cambiar la clave del certificado",
                new VBox(8,
                        texto("El certificado de ARCA no cambia: sólo se vuelve a proteger con la clave nueva."),
                        aviso("Las demás PC van a dejar de poder facturar hasta que se escriba la clave nueva en "
                                + "cada una (en esta misma pantalla, paso 2). Anote la clave nueva en un lugar seguro.",
                                "form-warning"),
                        nueva, repetida),
                Dialogs.CANCELAR, cambiar);

        Platform.runLater(nueva::requestFocus);

        if (alert.showAndWait().filter(r -> r == cambiar).isEmpty()) {
            return;
        }

        if (!nueva.getText().equals(repetida.getText())) {
            warning("Las dos claves no coinciden. No se cambió la clave.");
            return;
        }

        execute(() -> {
            mostrarConfiguracion(configuracionArca.cambiarClave(nueva.getText().toCharArray()));
            success("Se cambió la clave del certificado y quedó guardada en esta PC.\n\nEscriba la clave nueva en "
                    + "las demás PC que facturan (Facturación electrónica, paso 2).");
        });
    }

    /**
     * La clave no abre el certificado guardado. Si nadie la recuerda, la única
     * salida es descartar el certificado y generar otro en ARCA con la clave nueva.
     */
    private void ofrecerDescartarCertificado(String motivo, String claveNueva) {

        ButtonType reintentar = new ButtonType("Volver a escribirla", ButtonData.CANCEL_CLOSE);
        ButtonType descartar = new ButtonType("Perdí la clave…", ButtonData.OK_DONE);

        var alert = Dialogs.crear(Dialogs.Tipo.ADVERTENCIA, "La clave no corresponde",
                texto(motivo + "\n\nSi nadie recuerda la clave, se puede descartar el certificado guardado y "
                        + "generar uno nuevo en ARCA con esta clave."),
                reintentar, descartar);

        if (alert.showAndWait().filter(r -> r == descartar).isEmpty()) {
            txtClave.clear();
            txtClave.requestFocus();
            return;
        }

        boolean confirmado = Dialogs.confirmar("¿Descartar el certificado guardado?",
                "Se quita del sistema el certificado de ARCA y se deshabilita la facturación electrónica. Después "
                        + "hay que generar un certificado nuevo (pasos 5 a 8) y volver a habilitarla.\n\n"
                        + "Las demás PC tendrán que cargar la clave nueva. Hágalo sólo si nadie recuerda la clave "
                        + "anterior.", "Descartar certificado", true);

        if (!confirmado) {
            return;
        }

        execute(() -> {
            configuracionArca.descartarCertificado();
            ultimaPrueba = null;
            mostrarConfiguracion(configuracionArca.guardarClaveEnEstaPc(claveNueva.toCharArray()));
            success("Se descartó el certificado y se guardó la clave nueva en esta PC. Siga con el paso 5 para "
                    + "generar un certificado nuevo.");
        });
    }

    private static String descripcionCertificado(ConfiguracionArcaDto config) {

        if (!config.isCertificadoCargado()) {
            return config.isPedidoPendiente() ? "Pedido generado, falta importar el certificado de ARCA" : "Sin certificado";
        }

        Long dias = config.getDiasParaVencer();
        String vencimiento = config.getCertificadoVencimiento().format(FORMATO_FECHA);

        String estado = dias < 0 ? "venció el " + vencimiento
                : "vence el " + vencimiento + " (" + (dias == 0 ? "hoy" : "en " + dias + (dias == 1 ? " día" : " días")) + ")";

        return config.getCertificadoTitular() + " — " + estado
                + (config.isPedidoPendiente() ? ". Hay un pedido de certificado nuevo pendiente de importar." : "");
    }

    private void mostrarAvisos(ConfiguracionArcaDto config) {

        List<Label> avisos = new ArrayList<>();

        if (!config.isClaveConfigurada()) {
            avisos.add(aviso("Falta cargar en esta PC la clave del certificado (paso 2). Sin ella no se puede "
                    + "generar, importar ni usar el certificado.", "form-error"));
        } else if (Boolean.FALSE.equals(config.getClaveCorrecta())) {
            avisos.add(aviso("La clave del certificado de esta PC no abre el certificado guardado: esta PC no puede "
                    + "facturar. Corríjala en el paso 2.", "form-error"));
        }

        Long dias = config.getDiasParaVencer();

        if (dias != null && dias < 0) {
            avisos.add(aviso("El certificado de ARCA está vencido: no se pueden emitir facturas. Genere un pedido "
                    + "nuevo (paso 5) y repita los pasos 6 a 8.", "form-error"));
        } else if (config.isCertificadoPorVencer()) {
            avisos.add(aviso("El certificado de ARCA vence " + (dias == 0 ? "hoy" : "en " + dias
                    + (dias == 1 ? " día" : " días")) + ". Renuévelo antes: genere un pedido nuevo (paso 5) y "
                    + "repita los pasos 6 a 8. El certificado actual sigue en uso hasta importar el nuevo.",
                    "form-warning"));
        }

        if (config.isHabilitada() && config.getAmbiente() == AmbienteArca.HOMOLOGACION) {
            avisos.add(aviso("Está facturando en homologación (pruebas): las facturas que se emiten no tienen "
                    + "validez fiscal. Cuando termine de probar, pase a producción.", "form-warning"));
        }

        panelAvisos.getChildren().setAll(avisos);
        mostrar(panelAvisos, !avisos.isEmpty());
    }

    /** Datos de la empresa que usa la factura electrónica, con lo que falte completar. */
    private void mostrarEmpresa() {

        EmpresaDto empresa;

        try {
            empresa = empresaService.obtener();
        } catch (BusinessException e) {
            lblEmpresa.setText("No se pudieron consultar los datos de la empresa: " + e.getMessage());
            pasoEmpresa.estado("Sin verificar", false);
            return;
        } catch (RuntimeException e) {
            lblEmpresa.setText("No se pudieron consultar los datos de la empresa.");
            pasoEmpresa.estado("Sin verificar", false);
            return;
        }

        List<String> faltantes = new ArrayList<>();

        if (empresa.getCondicionIva() == null || !empresa.getCondicionIva().esDeEmisor()) {
            faltantes.add("la condición frente al IVA (responsable inscripto, monotributista o exento)");
        }

        if (empresa.getFechaInicioActividades() == null) {
            faltantes.add("la fecha de inicio de actividades");
        }

        String datos = "CUIT " + empresa.getCuitFormateado() + " — " + empresa.getRazonSocial()
                + (empresa.getCondicionIva() == null ? "" : " — " + empresa.getCondicionIva().getDescripcion())
                + (empresa.getFechaInicioActividades() == null ? ""
                : " — Inicio de actividades: " + empresa.getFechaInicioActividades().format(FORMATO_FECHA));

        lblEmpresa.setText(faltantes.isEmpty() ? datos
                : datos + "\nFalta completar " + String.join(" y ", faltantes) + ".");

        pasoEmpresa.estado(faltantes.isEmpty() ? "Completos" : "Faltan datos", faltantes.isEmpty());
    }

    private void mostrarPrueba() {

        resultadoPrueba.getChildren().clear();

        if (ultimaPrueba == null) {
            pasoProbar.estado(configuracion != null && configuracion.isHabilitada()
                    ? "No se probó en esta sesión" : "Pendiente", false);
            mostrar(resultadoPrueba, false);
            return;
        }

        for (PruebaConexionArca.Paso paso : ultimaPrueba.pasos()) {
            resultadoPrueba.getChildren().add(renglonPrueba(paso));
        }

        mostrar(resultadoPrueba, true);
        pasoProbar.estado(ultimaPrueba.exitosa() ? "Conexión correcta" : "Con errores", ultimaPrueba.exitosa());
    }

    private static Node renglonPrueba(PruebaConexionArca.Paso paso) {

        FontIcon icono = switch (paso.resultado()) {
            case CORRECTO -> icono(FontAwesomeSolid.CHECK_CIRCLE, "#2E7D32");
            case ERROR -> icono(FontAwesomeSolid.TIMES_CIRCLE, "#C62828");
            case SIN_VERIFICAR -> icono(FontAwesomeSolid.MINUS_CIRCLE, "#9CA3AF");
        };

        Label nombre = new Label(paso.nombre() + ":");
        nombre.setStyle("-fx-font-weight: bold;");
        nombre.setMinWidth(Region.USE_PREF_SIZE);

        Label detalle = texto(paso.detalle());
        HBox.setHgrow(detalle, Priority.ALWAYS);

        HBox renglon = new HBox(8, icono, nombre, detalle);
        renglon.setAlignment(Pos.TOP_LEFT);
        return renglon;
    }

    private static FontIcon icono(FontAwesomeSolid tipo, String color) {
        FontIcon icono = new FontIcon(tipo);
        icono.setIconSize(16);
        icono.setStyle("-fx-icon-color: " + color + ";");
        return icono;
    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    /** Guarda el ambiente y el punto de venta, sin cambiar si está habilitada. */
    private void guardar() {

        if (configuracion == null) {
            return;
        }

        AmbienteArca ambiente = cmbAmbiente.getValue();

        if (ambiente != configuracion.getAmbiente() && configuracion.isCertificadoCargado()
                && !confirm("Cambiar de ambiente", "El certificado actual es del ambiente "
                + configuracion.getAmbiente().getDescripcion() + " y no sirve para "
                + ambiente.getDescripcion() + ": va a tener que generar un pedido nuevo e importar otro "
                + "certificado (pasos 5 a 8). ¿Cambiar de ambiente?")) {
            cmbAmbiente.setValue(configuracion.getAmbiente());
            return;
        }

        if (ambiente == AmbienteArca.PRODUCCION && configuracion.isHabilitada()
                && configuracion.getAmbiente() != AmbienteArca.PRODUCCION
                && !confirm("Pasar a producción", "Desde ahora las facturas se emiten en producción y tienen "
                + "validez fiscal. ¿Continuar?")) {
            cmbAmbiente.setValue(configuracion.getAmbiente());
            return;
        }

        execute(() -> {
            mostrarConfiguracion(configuracionArca.guardar(ConfiguracionArcaDto.builder()
                    .ambiente(ambiente)
                    .puntoVenta(puntoVenta())
                    .habilitada(configuracion.isHabilitada())
                    .build()));
            ultimaPrueba = null;
            mostrarPrueba();
            success("Configuración de facturación electrónica guardada.");
        });
    }

    private Integer puntoVenta() {
        String texto = txtPuntoVenta.getText() == null ? "" : txtPuntoVenta.getText().trim();
        return texto.isEmpty() ? null : Integer.valueOf(texto);
    }

    private void generarPedido() {

        if (configuracion != null && configuracion.isCertificadoCargado()
                && !confirm("Generar un pedido nuevo", "Se genera una clave nueva para renovar o reemplazar el "
                + "certificado. El certificado actual sigue en uso hasta que importe el nuevo. ¿Continuar?")) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar el pedido de certificado");
        chooser.setInitialFileName("pedido-certificado-arca-" + LocalDate.now() + ".csr");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pedido de certificado (*.csr)", "*.csr"));

        File archivo = chooser.showSaveDialog(ventana());

        if (archivo == null) {
            return;
        }

        execute(() -> {

            String pedido = configuracionArca.generarPedidoCertificado();

            try {
                Files.writeString(archivo.toPath(), pedido, StandardCharsets.US_ASCII);
            } catch (IOException e) {
                throw new BusinessException("Se generó el pedido pero no se pudo guardar en " + archivo
                        + ": " + e.getMessage() + ". Vuelva a generarlo eligiendo otra carpeta.", e);
            } finally {
                copiar(pedido);
            }

            mostrarConfiguracion(configuracionArca.obtener());

            Dialogs.exito("Pedido de certificado generado", "Se guardó en:\n" + archivo
                    + "\n\nSu contenido también quedó copiado, para pegarlo en ARCA. Siga con el paso 6.");
        });
    }

    private void importarCertificado() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Certificado emitido por ARCA");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Certificados (*.crt, *.cer, *.pem)", "*.crt", "*.cer", "*.pem"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));

        File archivo = chooser.showOpenDialog(ventana());

        if (archivo == null) {
            return;
        }

        execute(() -> {
            mostrarConfiguracion(configuracionArca.importarCertificado(leer(archivo)));
            success("Certificado importado. Siga con el paso 8 (autorizarlo en ARCA) y después pruebe la conexión.");
        });
    }

    private void importarPkcs12() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Certificado con clave privada");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Certificado con clave (*.p12, *.pfx)", "*.p12", "*.pfx"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*"));

        File archivo = chooser.showOpenDialog(ventana());

        if (archivo == null) {
            return;
        }

        Optional<char[]> contrasenia = pedirContrasenia(archivo.getName());

        if (contrasenia.isEmpty()) {
            return;
        }

        execute(() -> {
            try {
                mostrarConfiguracion(configuracionArca.importarPkcs12(leer(archivo), contrasenia.get()));
            } finally {
                Arrays.fill(contrasenia.get(), '\0');
            }
            success("Certificado importado. Verifique que esté autorizado para facturar (paso 8) y pruebe la conexión.");
        });
    }

    private Optional<char[]> pedirContrasenia(String archivo) {

        PasswordField campo = new PasswordField();
        campo.setPromptText("Contraseña del archivo");

        ButtonType importar = new ButtonType("Importar", ButtonData.OK_DONE);

        var alert = Dialogs.crear(Dialogs.Tipo.CONFIRMACION, "Contraseña de " + archivo,
                new VBox(8, texto("Escriba la contraseña con que se protegió el archivo."), campo),
                Dialogs.CANCELAR, importar);

        Platform.runLater(campo::requestFocus);

        return alert.showAndWait()
                .filter(respuesta -> respuesta == importar)
                .map(respuesta -> campo.getText().toCharArray());
    }

    private void probarConexion() {

        TareaEnSegundoPlano.ejecutar("Probando la conexión con ARCA…",
                configuracionArca::probarConexion,
                prueba -> {
                    ultimaPrueba = prueba;
                    mostrarPrueba();

                    PruebaConexionArca.Paso error = prueba.primerError();

                    if (error == null) {
                        Dialogs.exito("Conexión correcta", "ARCA aceptó el certificado y el punto de venta."
                                + (configuracion != null && !configuracion.isHabilitada()
                                ? " Ya puede habilitar la facturación electrónica (paso 10)." : ""));
                    } else {
                        Dialogs.advertencia("La prueba de conexión encontró un problema",
                                error.nombre() + ": " + error.detalle());
                    }
                });
    }

    private void cambiarHabilitacion() {

        if (configuracion == null) {
            return;
        }

        boolean habilitar = !configuracion.isHabilitada();

        if (habilitar && !confirmarHabilitacion()) {
            return;
        }

        if (!habilitar && !Dialogs.confirmar("Deshabilitar la facturación electrónica",
                "El punto de venta dejará de ofrecer facturas. Los comprobantes pendientes de CAE quedan para "
                        + "cuando se vuelva a habilitar. ¿Deshabilitar?", "Deshabilitar", true)) {
            return;
        }

        execute(() -> {
            mostrarConfiguracion(configuracionArca.guardar(ConfiguracionArcaDto.builder()
                    .ambiente(configuracion.getAmbiente())
                    .puntoVenta(configuracion.getPuntoVenta())
                    .habilitada(habilitar)
                    .build()));
            success(habilitar ? "Facturación electrónica habilitada. El punto de venta ya ofrece facturas."
                    : "Facturación electrónica deshabilitada.");
        });
    }

    private boolean confirmarHabilitacion() {

        StringBuilder mensaje = new StringBuilder();

        if (ultimaPrueba == null || !ultimaPrueba.exitosa()) {
            mensaje.append("Todavía no hizo una prueba de conexión correcta. Se recomienda probarla antes (paso 9).\n\n");
        }

        if (configuracion.getAmbiente() == AmbienteArca.PRODUCCION) {
            mensaje.append("Las facturas se van a emitir en producción y tienen validez fiscal.");
        } else {
            mensaje.append("Está en homologación: las facturas que se emitan son de prueba y no tienen validez fiscal.");
        }

        return confirm("Habilitar la facturación electrónica", mensaje + "\n\n¿Habilitar?");
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    private Button enlaceArca() {
        Button boton = boton("Abrir el sitio de ARCA", "btn-secondary");
        boton.setOnAction(e -> hostServices.showDocument(GuiaArca.URL_CLAVE_FISCAL));
        return boton;
    }

    private static byte[] leer(File archivo) {

        try {
            if (Files.size(archivo.toPath()) > 1_000_000) {
                throw new BusinessException("El archivo es demasiado grande para ser un certificado.");
            }
            return Files.readAllBytes(archivo.toPath());
        } catch (IOException e) {
            throw new BusinessException("No se pudo leer el archivo " + archivo + ": " + e.getMessage(), e);
        }
    }

    private static void copiar(String texto) {
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(texto);
        Clipboard.getSystemClipboard().setContent(contenido);
    }

    private Window ventana() {
        return contenedorPasos.getScene().getWindow();
    }

    private static Label aviso(String texto, String estilo) {
        Label label = texto(texto);
        label.getStyleClass().add(estilo);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private static Label texto(String texto) {
        Label label = new Label(texto);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static Button boton(String texto, String estilo) {
        Button boton = new Button(texto);
        boton.getStyleClass().add(estilo);
        return boton;
    }

    private static HBox acciones(Node... nodos) {
        HBox acciones = new HBox(8, nodos);
        acciones.setAlignment(Pos.CENTER_LEFT);
        return acciones;
    }

    private static void mostrar(Node nodo, boolean visible) {
        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    /** Un paso de la guía: número, título, estado (hecho o no) y su contenido. */
    private static final class Paso {

        private final VBox caja;
        private final Label lblEstado = new Label();
        private final Label lblMotivo = new Label();

        Paso(int numero, String titulo, Node... contenido) {

            Label lblNumero = new Label(String.valueOf(numero));
            lblNumero.getStyleClass().add("paso-numero");

            Label lblTitulo = new Label(titulo);
            lblTitulo.getStyleClass().add("paso-titulo");

            lblEstado.getStyleClass().add("paso-estado");

            Region espacio = new Region();
            HBox.setHgrow(espacio, Priority.ALWAYS);

            HBox encabezado = new HBox(10, lblNumero, lblTitulo, espacio, lblEstado);
            encabezado.setAlignment(Pos.CENTER_LEFT);

            VBox cuerpo = new VBox(8, contenido);
            lblMotivo.getStyleClass().add("form-hint");
            lblMotivo.setWrapText(true);
            mostrar(lblMotivo, false);
            cuerpo.getChildren().add(lblMotivo);
            cuerpo.setPadding(new Insets(0, 0, 0, 38));

            caja = new VBox(8, encabezado, cuerpo);
            caja.getStyleClass().add("paso-guia");
        }

        VBox caja() {
            return caja;
        }

        /** Por qué las acciones del paso están deshabilitadas; null si no lo están. */
        void motivo(String texto) {
            lblMotivo.setText(texto == null ? "" : texto);
            mostrar(lblMotivo, texto != null);
        }

        void estado(String texto, boolean hecho) {

            lblEstado.setText(hecho ? "✓ " + texto : texto);
            caja.getStyleClass().remove(HECHO);

            if (hecho) {
                caja.getStyleClass().add(HECHO);
            }
        }
    }

}
