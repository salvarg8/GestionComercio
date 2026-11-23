package org.gestionComercio.controller.producto;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.base.ErroresPorCampo;
import org.gestionComercio.dto.producto.ActualizacionPrecioDto;
import org.gestionComercio.dto.producto.FiltroActualizacionPrecios;
import org.gestionComercio.dto.producto.PrecioActualizadoDto;
import org.gestionComercio.enums.PrecioActualizado;
import org.gestionComercio.enums.Redondeo;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.service.ActualizacionPreciosService;
import org.gestionComercio.service.CategoriaService;
import org.gestionComercio.service.MarcaService;
import org.gestionComercio.service.ProveedorService;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.util.NumberUtils;
import org.gestionComercio.util.dialog.Dialogs;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Actualización masiva de precios: se elige el criterio, se revisa la vista
 * previa y se aplica. Cualquier cambio en el criterio descarta la vista
 * previa, así lo que se aplica es siempre lo que se ve. Abajo, el historial
 * de las actualizaciones aplicadas.
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class ActualizacionPreciosController extends AbstractController {

    /** Una opción de los combos de filtro; id null = sin filtro. */
    private record Opcion(Long id, String texto) {

        @Override
        public String toString() {
            return texto;
        }
    }

    private final ActualizacionPreciosService actualizacionPreciosService;
    private final CategoriaService categoriaService;
    private final MarcaService marcaService;
    private final ProveedorService proveedorService;

    @FXML
    private TextField txtPorcentaje;

    @FXML
    private Label lblErrorPorcentaje;

    @FXML
    private ComboBox<PrecioActualizado> cmbPrecio;

    @FXML
    private ComboBox<Redondeo> cmbRedondeo;

    @FXML
    private ComboBox<Opcion> cmbCategoria;

    @FXML
    private ComboBox<Opcion> cmbMarca;

    @FXML
    private ComboBox<Opcion> cmbProveedor;

    @FXML
    private Button btnPrevisualizar;

    @FXML
    private Label lblResumen;

    @FXML
    private TableView<PrecioActualizadoDto> tblVistaPrevia;

    @FXML
    private TableColumn<PrecioActualizadoDto, String> colCodigo;

    @FXML
    private TableColumn<PrecioActualizadoDto, String> colProducto;

    @FXML
    private TableColumn<PrecioActualizadoDto, String> colCategoria;

    @FXML
    private TableColumn<PrecioActualizadoDto, String> colCompraAnterior;

    @FXML
    private TableColumn<PrecioActualizadoDto, String> colCompraNueva;

    @FXML
    private TableColumn<PrecioActualizadoDto, String> colVentaAnterior;

    @FXML
    private TableColumn<PrecioActualizadoDto, String> colVentaNueva;

    @FXML
    private Button btnDescartar;

    @FXML
    private Button btnAplicar;

    @FXML
    private TableView<ActualizacionPrecioDto> tblHistorial;

    @FXML
    private TableColumn<ActualizacionPrecioDto, String> colHistFecha;

    @FXML
    private TableColumn<ActualizacionPrecioDto, String> colHistUsuario;

    @FXML
    private TableColumn<ActualizacionPrecioDto, String> colHistPorcentaje;

    @FXML
    private TableColumn<ActualizacionPrecioDto, String> colHistPrecio;

    @FXML
    private TableColumn<ActualizacionPrecioDto, String> colHistFiltro;

    @FXML
    private TableColumn<ActualizacionPrecioDto, String> colHistCantidad;

    private final ErroresPorCampo errores = new ErroresPorCampo();

    /** Criterio de la vista previa que se muestra; null si no hay vista previa vigente. */
    private FiltroActualizacionPrecios filtroPrevisto;

    @Override
    protected void initializeComponents() {

        cmbPrecio.getItems().setAll(PrecioActualizado.values());
        cmbPrecio.setValue(PrecioActualizado.VENTA);
        cmbRedondeo.getItems().setAll(Redondeo.values());
        cmbRedondeo.setValue(Redondeo.SIN_REDONDEO);

        errores.registrar("porcentaje", txtPorcentaje, lblErrorPorcentaje, txtPorcentaje.textProperty());

        cargarFiltros();
        configurarTablas();
        descartarVistaPrevia();
        cargarHistorial();
    }

    @Override
    protected void initializeEvents() {

        btnPrevisualizar.setOnAction(e -> previsualizar());
        txtPorcentaje.setOnAction(e -> previsualizar());
        btnAplicar.setOnAction(e -> aplicar());
        btnDescartar.setOnAction(e -> descartarVistaPrevia());

        // Cualquier cambio en el criterio deja vieja la vista previa.
        for (ObservableValue<?> valor : List.<ObservableValue<?>>of(txtPorcentaje.textProperty(),
                cmbPrecio.valueProperty(), cmbRedondeo.valueProperty(), cmbCategoria.valueProperty(),
                cmbMarca.valueProperty(), cmbProveedor.valueProperty())) {
            valor.addListener((obs, o, n) -> {
                if (filtroPrevisto != null) {
                    descartarVistaPrevia();
                }
            });
        }

        tblHistorial.setRowFactory(tabla -> {
            TableRow<ActualizacionPrecioDto> fila = new TableRow<>();
            fila.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !fila.isEmpty()) {
                    mostrarDetalle(fila.getItem());
                }
            });
            return fila;
        });
    }

    private void cargarFiltros() {

        execute(() -> {

            cmbCategoria.getItems().setAll(opciones("Todas las categorías",
                    categoriaService.findParaSeleccion().stream().map(c -> new Opcion(c.getId(), c.getNombre())).toList()));

            cmbMarca.getItems().setAll(opciones("Todas las marcas",
                    marcaService.findParaSeleccion().stream().map(m -> new Opcion(m.getId(), m.getNombre())).toList()));

            cmbProveedor.getItems().setAll(opciones("Todos los proveedores",
                    proveedorService.findParaSeleccion().stream()
                            .map(p -> new Opcion(p.getId(), p.getRazonSocial())).toList()));
        });

        for (ComboBox<Opcion> combo : List.of(cmbCategoria, cmbMarca, cmbProveedor)) {
            if (!combo.getItems().isEmpty()) {
                combo.setValue(combo.getItems().get(0));
            }
        }
    }

    private static List<Opcion> opciones(String todas, List<Opcion> lista) {
        List<Opcion> opciones = new ArrayList<>();
        opciones.add(new Opcion(null, todas));
        opciones.addAll(lista);
        return opciones;
    }

    private void configurarTablas() {

        columna(colCodigo, PrecioActualizadoDto::codigo);
        columna(colProducto, PrecioActualizadoDto::nombre);
        columna(colCategoria, PrecioActualizadoDto::categoria);
        columna(colCompraAnterior, p -> CurrencyUtils.format(p.precioCompraAnterior()));
        columna(colCompraNueva, p -> CurrencyUtils.format(p.precioCompraNuevo()));
        columna(colVentaAnterior, p -> CurrencyUtils.format(p.precioVentaAnterior()));
        columna(colVentaNueva, p -> CurrencyUtils.format(p.precioVentaNuevo()));

        columna(colHistFecha, a -> DateUtils.format(a.fecha()));
        columna(colHistUsuario, ActualizacionPrecioDto::usuario);
        columna(colHistPorcentaje, a -> porcentaje(a.porcentaje()));
        columna(colHistPrecio, a -> a.precio().getDescripcion()
                + (a.redondeo() == null || a.redondeo() == Redondeo.SIN_REDONDEO
                ? "" : " (" + a.redondeo().getDescripcion().toLowerCase() + ")"));
        columna(colHistFiltro, ActualizacionPrecioDto::filtro);
        columna(colHistCantidad, a -> String.valueOf(a.cantidadProductos()));
    }

    private static <T> void columna(TableColumn<T, String> columna, Function<T, String> valor) {
        columna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
    }

    // ------------------------------------------------------------------
    // Vista previa y aplicación
    // ------------------------------------------------------------------

    private void previsualizar() {

        errores.limpiar();

        BigDecimal porcentaje;

        try {
            porcentaje = NumberUtils.parseDecimal(txtPorcentaje.getText() == null
                    ? null : txtPorcentaje.getText().replace("%", ""));
        } catch (NumberFormatException e) {
            errores.mostrar("porcentaje", "Ingrese un número, por ejemplo 15 o -10.");
            return;
        }

        FiltroActualizacionPrecios filtro = new FiltroActualizacionPrecios(
                porcentaje,
                cmbPrecio.getValue(),
                cmbRedondeo.getValue(),
                id(cmbCategoria),
                id(cmbMarca),
                id(cmbProveedor));

        execute(() -> {
            try {
                List<PrecioActualizadoDto> propuestas = actualizacionPreciosService.previsualizar(filtro);

                tblVistaPrevia.getItems().setAll(propuestas);
                filtroPrevisto = propuestas.isEmpty() ? null : filtro;

                lblResumen.setText(propuestas.isEmpty()
                        ? "Ningún producto activo cumple el filtro."
                        : resumen(filtro, propuestas.size()) + ". Revise los precios nuevos y presione Aplicar.");
                btnAplicar.setDisable(propuestas.isEmpty());
                btnDescartar.setDisable(false);

            } catch (ValidationException ex) {
                errores.mostrar(ex.getValidationResult());
            }
        });
    }

    private void aplicar() {

        if (filtroPrevisto == null || tblVistaPrevia.getItems().isEmpty()) {
            return;
        }

        FiltroActualizacionPrecios filtro = filtroPrevisto;
        List<PrecioActualizadoDto> vistaPrevia = List.copyOf(tblVistaPrevia.getItems());

        if (!confirm("Aplicar actualización de precios", resumen(filtro, vistaPrevia.size())
                + ".\n\nLos precios cambian en todas las PC y la actualización queda registrada en el historial. "
                + "¿Desea aplicarla?")) {
            return;
        }

        execute(() -> {

            ActualizacionPrecioDto registro = actualizacionPreciosService.aplicar(filtro, vistaPrevia);

            descartarVistaPrevia();
            cargarHistorial();

            success("Se actualizaron los precios de " + registro.cantidadProductos() + " productos.");
        });
    }

    private void descartarVistaPrevia() {

        filtroPrevisto = null;
        tblVistaPrevia.getItems().clear();
        lblResumen.setText("");
        btnAplicar.setDisable(true);
        btnDescartar.setDisable(true);
    }

    /** "Precio de venta de 12 productos: +15 %, redondeado al peso". */
    private static String resumen(FiltroActualizacionPrecios filtro, int cantidad) {

        return filtro.precio().getDescripcion() + " de " + cantidad
                + (cantidad == 1 ? " producto: " : " productos: ") + porcentaje(filtro.porcentaje())
                + (filtro.redondeo() == Redondeo.SIN_REDONDEO
                ? "" : ", redondeado " + filtro.redondeo().getDescripcion().toLowerCase());
    }

    private static String porcentaje(BigDecimal valor) {
        if (valor == null) {
            return "";
        }
        return (valor.signum() > 0 ? "+" : "") + NumberUtils.formatDecimalEditable(valor) + " %";
    }

    private static Long id(ComboBox<Opcion> combo) {
        return combo.getValue() == null ? null : combo.getValue().id();
    }

    // ------------------------------------------------------------------
    // Historial
    // ------------------------------------------------------------------

    private void cargarHistorial() {
        execute(() -> tblHistorial.getItems().setAll(actualizacionPreciosService.historial()));
    }

    private void mostrarDetalle(ActualizacionPrecioDto actualizacion) {

        execute(() -> {

            List<PrecioActualizadoDto> detalle = actualizacionPreciosService.detalle(actualizacion.id());

            TableView<PrecioActualizadoDto> tabla = new TableView<>();
            tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            tabla.setPrefSize(760, 360);

            tabla.getColumns().add(nuevaColumna("Código", 90, PrecioActualizadoDto::codigo));
            tabla.getColumns().add(nuevaColumna("Producto", 230, PrecioActualizadoDto::nombre));
            tabla.getColumns().add(nuevaColumna("Compra anterior", 105,
                    p -> CurrencyUtils.format(p.precioCompraAnterior())));
            tabla.getColumns().add(nuevaColumna("Compra nueva", 105, p -> CurrencyUtils.format(p.precioCompraNuevo())));
            tabla.getColumns().add(nuevaColumna("Venta anterior", 105,
                    p -> CurrencyUtils.format(p.precioVentaAnterior())));
            tabla.getColumns().add(nuevaColumna("Venta nueva", 105, p -> CurrencyUtils.format(p.precioVentaNuevo())));
            tabla.getItems().setAll(detalle);

            Dialogs.crear(Dialogs.Tipo.INFORMACION,
                    "Actualización del " + DateUtils.format(actualizacion.fecha()) + " — "
                            + porcentaje(actualizacion.porcentaje()) + " — " + actualizacion.filtro(),
                    tabla, Dialogs.ACEPTAR).showAndWait();
        });
    }

    private static TableColumn<PrecioActualizadoDto, String> nuevaColumna(
            String titulo, double ancho, Function<PrecioActualizadoDto, String> valor) {

        TableColumn<PrecioActualizadoDto, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna(columna, valor);
        return columna;
    }
}
