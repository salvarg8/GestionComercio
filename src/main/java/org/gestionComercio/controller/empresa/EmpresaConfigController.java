package org.gestionComercio.controller.empresa;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.base.ErroresPorCampo;
import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.EmpresaService;
import org.gestionComercio.util.ImageUtils;
import org.gestionComercio.view.component.SelectorFecha;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Datos de la única empresa. Con EMPRESA_VER se consultan; sin EMPRESA_EDITAR
 * el formulario queda de sólo lectura.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class EmpresaConfigController extends AbstractController {

    private final EmpresaService empresaService;
    private final SesionUsuario sesionUsuario;

    @FXML
    private Label lblSoloLectura;

    @FXML
    private TextField txtRazonSocial;

    @FXML
    private Label lblErrorRazonSocial;

    @FXML
    private TextField txtNombreFantasia;

    @FXML
    private Label lblErrorNombreFantasia;

    @FXML
    private TextField txtCuit;

    @FXML
    private Label lblErrorCuit;

    @FXML
    private TextField txtIngresosBrutos;

    @FXML
    private Label lblErrorIngresosBrutos;

    @FXML
    private ComboBox<CondicionIVA> cmbCondicionIva;

    @FXML
    private DatePicker dpInicioActividades;

    @FXML
    private Label lblErrorInicioActividades;

    @FXML
    private Label lblErrorCondicionIva;

    @FXML
    private TextField txtTelefono;

    @FXML
    private Label lblErrorTelefono;

    @FXML
    private TextField txtEmail;

    @FXML
    private Label lblErrorEmail;

    @FXML
    private TextField txtDireccion;

    @FXML
    private Label lblErrorDireccion;

    @FXML
    private ImageView imgLogo;

    @FXML
    private Label lblSinLogo;

    @FXML
    private Button btnSeleccionarLogo;

    @FXML
    private Button btnQuitarLogo;

    @FXML
    private Label lblErrorLogo;

    @FXML
    private Button btnDescartar;

    @FXML
    private Button btnGuardar;

    /** Datos tal como están guardados; null si no se pudieron cargar. */
    private EmpresaDto empresaGuardada;

    /** Ruta del logo guardado que se conserva; null si se quitó o no hay. */
    private String rutaLogo;

    /** Archivo elegido por el usuario y todavía no guardado; null si no se cambió. */
    private Path logoSeleccionado;

    private final ErroresPorCampo errores = new ErroresPorCampo();

    @Override
    protected void initializeComponents() {

        // Consumidor final no es una opción válida para el comercio.
        cmbCondicionIva.getItems().setAll(
                CondicionIVA.RESPONSABLE_INSCRIPTO,
                CondicionIVA.MONOTRIBUTISTA,
                CondicionIVA.EXENTO
        );

        SelectorFecha.configurar(dpInicioActividades);

        errores
                .registrar("razonSocial", txtRazonSocial, lblErrorRazonSocial, txtRazonSocial.textProperty())
                .registrar("nombreFantasia", txtNombreFantasia, lblErrorNombreFantasia,
                        txtNombreFantasia.textProperty())
                .registrar("cuit", txtCuit, lblErrorCuit, txtCuit.textProperty())
                .registrar("ingresosBrutos", txtIngresosBrutos, lblErrorIngresosBrutos,
                        txtIngresosBrutos.textProperty())
                .registrar("condicionIva", cmbCondicionIva, lblErrorCondicionIva, cmbCondicionIva.valueProperty())
                .registrar("fechaInicioActividades", dpInicioActividades, lblErrorInicioActividades,
                        dpInicioActividades.valueProperty())
                .registrar("telefono", txtTelefono, lblErrorTelefono, txtTelefono.textProperty())
                .registrar("email", txtEmail, lblErrorEmail, txtEmail.textProperty())
                .registrar("direccion", txtDireccion, lblErrorDireccion, txtDireccion.textProperty())
                .registrar("logo", btnSeleccionarLogo, lblErrorLogo, imgLogo.imageProperty());

        aplicarPermisos();

        execute(() -> cargar(empresaService.obtener()));

        if (empresaGuardada == null) {
            // Sin datos no hay nada que guardar.
            btnGuardar.setDisable(true);
            btnDescartar.setDisable(true);
        }
    }

    @Override
    protected void initializeEvents() {

        btnGuardar.setOnAction(e -> guardar());

        btnDescartar.setOnAction(e -> {
            if (empresaGuardada != null) {
                cargar(empresaGuardada);
            }
        });

        btnSeleccionarLogo.setOnAction(e -> seleccionarLogo());

        btnQuitarLogo.setOnAction(e -> {
            rutaLogo = null;
            logoSeleccionado = null;
            mostrarLogo(null);
        });
    }

    /** Sin EMPRESA_EDITAR los datos se ven pero no se pueden cambiar. */
    private void aplicarPermisos() {

        boolean puedeEditar = sesionUsuario.tienePermiso(PermisoCodigo.EMPRESA_EDITAR);

        for (TextField campo : new TextField[]{
                txtRazonSocial, txtNombreFantasia, txtCuit, txtIngresosBrutos,
                txtTelefono, txtEmail, txtDireccion}) {
            campo.setEditable(puedeEditar);
            campo.setFocusTraversable(puedeEditar);
        }

        cmbCondicionIva.setDisable(!puedeEditar);
        dpInicioActividades.setDisable(!puedeEditar);

        mostrar(lblSoloLectura, !puedeEditar);
        mostrar(btnSeleccionarLogo, puedeEditar);
        mostrar(btnQuitarLogo, puedeEditar);
        mostrar(btnGuardar, puedeEditar);
        mostrar(btnDescartar, puedeEditar);
    }

    private void cargar(EmpresaDto empresa) {

        empresaGuardada = empresa;
        rutaLogo = empresa.getLogo();
        logoSeleccionado = null;

        txtRazonSocial.setText(empresa.getRazonSocial());
        txtNombreFantasia.setText(empresa.getNombreFantasia());
        txtCuit.setText(empresa.getCuitFormateado());
        txtIngresosBrutos.setText(empresa.getIngresosBrutos());
        txtTelefono.setText(empresa.getTelefono());
        txtEmail.setText(empresa.getEmail());
        txtDireccion.setText(empresa.getDireccion());

        // Un dato viejo (por ejemplo, consumidor final) se muestra igual, para que se vea qué hay que corregir.
        if (empresa.getCondicionIva() != null && !cmbCondicionIva.getItems().contains(empresa.getCondicionIva())) {
            cmbCondicionIva.getItems().add(empresa.getCondicionIva());
        }
        cmbCondicionIva.setValue(empresa.getCondicionIva());
        dpInicioActividades.setValue(empresa.getFechaInicioActividades());

        mostrarLogo(ImageUtils.cargar(rutaLogo));

        errores.limpiar();

        if (rutaLogo != null && imgLogo.getImage() == null) {
            errores.mostrar("logo", "No se encontró la imagen del logo (" + rutaLogo + "). Seleccione otra.");
        }
    }

    // ------------------------------------------------------------------
    // Logo
    // ------------------------------------------------------------------

    private void seleccionarLogo() {

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar logo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Imágenes (PNG, JPG, GIF, BMP)",
                ImageUtils.EXTENSIONES_PERMITIDAS.stream().map(ext -> "*." + ext).toList()
        ));

        File archivo = chooser.showOpenDialog(btnSeleccionarLogo.getScene().getWindow());

        if (archivo == null) {
            return;
        }

        Path ruta = archivo.toPath();

        String error = ImageUtils.validarArchivoLogo(ruta);
        Image imagen = error == null ? ImageUtils.cargar(ruta.toString()) : null;

        if (error == null && imagen == null) {
            error = "El archivo seleccionado no es una imagen válida.";
        }

        if (error != null) {
            // Si ya había un error del logo, se reemplaza por el nuevo.
            errores.limpiar("logo");
            errores.mostrar("logo", error);
            return;
        }

        logoSeleccionado = ruta;
        mostrarLogo(imagen);
    }

    private void mostrarLogo(Image imagen) {
        imgLogo.setImage(imagen);
        mostrar(lblSinLogo, imagen == null);
    }

    // ------------------------------------------------------------------
    // Guardar
    // ------------------------------------------------------------------

    private void guardar() {

        // Una fecha escrita a mano no pasa al valor del DatePicker hasta que pierde el foco.
        dpInicioActividades.setValue(dpInicioActividades.getConverter()
                .fromString(dpInicioActividades.getEditor().getText()));

        errores.limpiar();

        execute(() -> {

            // El logo nuevo se copia antes de guardar; si el guardado falla, se borra la copia.
            String copiaLogo = copiarLogoSeleccionado();

            try {

                EmpresaDto guardada = empresaService.actualizar(
                        construirDto(copiaLogo != null ? copiaLogo : rutaLogo));

                String logoAnterior = empresaGuardada.getLogo();
                if (!Objects.equals(logoAnterior, guardada.getLogo())) {
                    ImageUtils.eliminarLogoGuardado(logoAnterior);
                }

                cargar(guardada);
                success("Datos de la empresa actualizados correctamente.");

            } catch (ValidationException ex) {
                ImageUtils.eliminarLogoGuardado(copiaLogo);
                errores.mostrar(ex.getValidationResult());
            } catch (RuntimeException ex) {
                ImageUtils.eliminarLogoGuardado(copiaLogo);
                throw ex;
            }
        });
    }

    private String copiarLogoSeleccionado() {

        if (logoSeleccionado == null) {
            return null;
        }

        try {
            return ImageUtils.guardarLogo(logoSeleccionado);
        } catch (IOException ex) {
            throw new BusinessException("No se pudo copiar el logo seleccionado: " + ex.getMessage(), ex);
        }
    }

    private EmpresaDto construirDto(String logo) {

        return EmpresaDto.builder()
                .id(empresaGuardada.getId())
                .razonSocial(txtRazonSocial.getText())
                .nombreFantasia(txtNombreFantasia.getText())
                .cuit(txtCuit.getText())
                .ingresosBrutos(txtIngresosBrutos.getText())
                .condicionIva(cmbCondicionIva.getValue())
                .fechaInicioActividades(dpInicioActividades.getValue())
                .telefono(txtTelefono.getText())
                .email(txtEmail.getText())
                .direccion(txtDireccion.getText())
                .logo(logo)
                .build();
    }

    private void mostrar(Control control, boolean visible) {
        control.setVisible(visible);
        control.setManaged(visible);
    }
}
