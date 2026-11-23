package org.gestionComercio.controller.inicio;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.dto.arca.ConfiguracionArcaDto;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.dto.inventario.ResumenStock;
import org.gestionComercio.dto.venta.ResumenVentas;
import org.gestionComercio.dto.venta.VentasPorCaja;
import org.gestionComercio.enums.EstadoCaja;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.CajaService;
import org.gestionComercio.service.ConfiguracionArcaService;
import org.gestionComercio.service.ConsultaStockService;
import org.gestionComercio.service.FacturacionElectronicaService;
import org.gestionComercio.service.VentaService;
import org.gestionComercio.util.CurrencyUtils;
import org.gestionComercio.util.DateUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Pantalla que se muestra al iniciar sesión, con un resumen del día y
 * accesos rápidos. Cada indicador aparece sólo si el usuario tiene el
 * permiso para ver ese dato:
 * <ul>
 *     <li>ventas del día: con VENTA_VER (quien ve las ventas de todos, como
 *         el administrador) el total del día y lo vendido en cada caja; si
 *         no, con VENTA_CREAR (un vendedor) lo vendido hoy en sus cajas;</li>
 *     <li>estado de la caja del usuario: CAJA_VER;</li>
 *     <li>productos con stock bajo y sin stock: INVENTARIO_VER.</li>
 * </ul>
 * Los datos se consultan cada vez que se abre la pantalla (y con "Actualizar").
 */
@Controller
@RequiredArgsConstructor
@Scope("prototype")
public class InicioController extends AbstractController {

    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.of("es", "AR"));

    /** Alto de cada fila de la tabla de cajas, para que muestre todas sin barra de desplazamiento. */
    private static final double ALTO_FILA = 32;

    @FXML
    private Label lblSaludo;

    @FXML
    private Label lblFecha;

    @FXML
    private Button btnActualizar;

    @FXML
    private VBox seccionAvisos;

    @FXML
    private VBox seccionResumen;

    @FXML
    private VBox tarjetaVentasCantidad;

    @FXML
    private Label lblVentasCantidadTitulo;

    @FXML
    private Label lblVentasCantidad;

    @FXML
    private Label lblVentasAlcance;

    @FXML
    private VBox tarjetaVentasImporte;

    @FXML
    private Label lblVentasImporteTitulo;

    @FXML
    private Label lblVentasImporte;

    @FXML
    private VBox tarjetaCaja;

    @FXML
    private Label lblCajaEstado;

    @FXML
    private Label lblCajaDetalle;

    @FXML
    private VBox tarjetaStockBajo;

    @FXML
    private Label lblStockBajo;

    @FXML
    private VBox tarjetaSinStock;

    @FXML
    private Label lblSinStock;

    @FXML
    private VBox seccionVentasPorCaja;

    @FXML
    private TableView<VentasPorCaja> tblVentasPorCaja;

    @FXML
    private TableColumn<VentasPorCaja, String> colCajaUsuario;

    @FXML
    private TableColumn<VentasPorCaja, String> colCajaApertura;

    @FXML
    private TableColumn<VentasPorCaja, String> colCajaEstado;

    @FXML
    private TableColumn<VentasPorCaja, String> colCajaCantidad;

    @FXML
    private TableColumn<VentasPorCaja, String> colCajaTotal;

    @FXML
    private VBox seccionAccesos;

    @FXML
    private Button btnNuevaVenta;

    @FXML
    private Button btnProductos;

    @FXML
    private Button btnInventario;

    @FXML
    private Label lblSinContenido;

    private final VentaService ventaService;
    private final CajaService cajaService;
    private final ConsultaStockService consultaStockService;
    private final SesionUsuario sesionUsuario;
    private final Navigator navigator;
    private final ConfiguracionArcaService configuracionArca;
    private final FacturacionElectronicaService facturacion;

    /** Ve las ventas de todas las cajas (VENTA_VER). */
    private boolean veVentasDeTodos;

    /** Sin VENTA_VER, ve sólo lo que vendió en sus cajas (VENTA_CREAR). */
    private boolean veVentasPropias;

    private boolean veCaja;
    private boolean veStock;
    private boolean veCertificadoArca;
    private boolean vePendientesCae;

    @Override
    protected void initializeComponents() {

        lblSaludo.setText("Hola, " + sesionUsuario.getUsuario().getNombre());

        veVentasDeTodos = sesionUsuario.tienePermiso(PermisoCodigo.VENTA_VER);
        veVentasPropias = !veVentasDeTodos && sesionUsuario.tienePermiso(PermisoCodigo.VENTA_CREAR);
        veCaja = sesionUsuario.tienePermiso(PermisoCodigo.CAJA_VER);
        veStock = sesionUsuario.tienePermiso(PermisoCodigo.INVENTARIO_VER);
        veCertificadoArca = navigator.puedeAcceder(AppView.FACTURACION_CONFIG);
        vePendientesCae = navigator.puedeAcceder(AppView.FACTURACION_PENDIENTES);

        configurarTarjetasDeVentas();
        configurarTablaVentasPorCaja();

        boolean veVentas = veVentasDeTodos || veVentasPropias;
        mostrar(tarjetaVentasCantidad, veVentas);
        mostrar(tarjetaVentasImporte, veVentas);
        mostrar(seccionVentasPorCaja, veVentasDeTodos);
        mostrar(tarjetaCaja, veCaja);
        mostrar(tarjetaStockBajo, veStock);
        mostrar(tarjetaSinStock, veStock);
        boolean hayIndicadores = veVentas || veCaja || veStock;
        mostrar(seccionResumen, hayIndicadores);

        boolean nuevaVenta = navigator.puedeAcceder(AppView.PUNTO_VENTA);
        boolean productos = navigator.puedeAcceder(AppView.PRODUCTO_LIST);
        boolean inventario = navigator.puedeAcceder(AppView.STOCK_CONSULTA);
        mostrar(btnNuevaVenta, nuevaVenta);
        mostrar(btnProductos, productos);
        mostrar(btnInventario, inventario);
        boolean hayAccesos = nuevaVenta || productos || inventario;
        mostrar(seccionAccesos, hayAccesos);

        mostrar(lblSinContenido, !hayIndicadores && !hayAccesos);

        cargar();
    }

    @Override
    protected void initializeEvents() {

        btnActualizar.setOnAction(e -> cargar());
        btnNuevaVenta.setOnAction(e -> navigator.navigate(AppView.PUNTO_VENTA));
        btnProductos.setOnAction(e -> navigator.navigate(AppView.PRODUCTO_LIST));
        btnInventario.setOnAction(e -> navigator.navigate(AppView.STOCK_CONSULTA));
    }

    private void configurarTarjetasDeVentas() {

        if (veVentasDeTodos) {
            lblVentasCantidadTitulo.setText("Ventas de hoy");
            lblVentasImporteTitulo.setText("Importe vendido hoy");
            lblVentasAlcance.setText("Todas las cajas");
        } else {
            lblVentasCantidadTitulo.setText("Mis ventas de hoy");
            lblVentasImporteTitulo.setText("Vendido hoy en mi caja");
            lblVentasAlcance.setText("Sólo mis cajas");
        }
    }

    private void configurarTablaVentasPorCaja() {

        columna(colCajaUsuario, VentasPorCaja::usuarioNombreCompleto);
        columna(colCajaApertura, v -> DateUtils.format(v.fechaApertura()));
        columna(colCajaEstado, v -> v.estadoCaja() == EstadoCaja.ABIERTA ? "Abierta" : "Cerrada");
        columna(colCajaCantidad, v -> String.valueOf(v.cantidad()));
        columna(colCajaTotal, v -> CurrencyUtils.format(v.total()));

        tblVentasPorCaja.setFixedCellSize(ALTO_FILA);
    }

    /** Consulta los indicadores visibles. Cada uno por separado, para que una falla no oculte al resto. */
    private void cargar() {

        lblFecha.setText(capitalizar(FORMATO_FECHA.format(LocalDate.now())));

        if (veVentasDeTodos) {
            execute(() -> mostrarVentasPorCaja(ventaService.ventasDelDiaPorCaja()));
        } else if (veVentasPropias) {
            execute(() -> mostrarVentas(ventaService.ventasDelDiaPropias()));
        }

        if (veCaja) {
            execute(() -> mostrarCaja(cajaService.obtenerCajaAbierta().orElse(null)));
        }

        if (veStock) {
            execute(() -> mostrarStock(consultaStockService.resumen()));
        }

        mostrarAvisosFacturacion();
    }

    /**
     * Avisa si el certificado de ARCA está vencido o por vencer
     * (FACTURACION_CONFIGURAR) y si hay comprobantes sin CAE
     * (FACTURACION_REINTENTAR). Un error al consultar no se muestra: son avisos.
     */
    private void mostrarAvisosFacturacion() {

        List<Node> avisos = new ArrayList<>();

        if (veCertificadoArca) {
            try {
                ConfiguracionArcaDto config = configuracionArca.obtener();
                Long dias = config.getDiasParaVencer();

                if (dias != null && (config.isHabilitada() || dias >= 0) && config.isCertificadoPorVencer()) {
                    String texto = dias < 0
                            ? "El certificado de ARCA está vencido: no se pueden emitir facturas electrónicas."
                            : "El certificado de ARCA vence " + (dias == 0 ? "hoy" : "en " + dias
                            + (dias == 1 ? " día" : " días")) + ". Renuévelo para seguir facturando.";
                    avisos.add(aviso(texto, dias < 0 ? "form-error" : "form-warning",
                            "Renovar certificado", AppView.FACTURACION_CONFIG));
                }
            } catch (RuntimeException e) {
                // Sin aviso.
            }
        }

        if (vePendientesCae) {
            try {
                int pendientes = facturacion.listarSinAutorizar().size();

                if (pendientes > 0) {
                    avisos.add(aviso(pendientes == 1 ? "Hay 1 comprobante electrónico sin CAE."
                                    : "Hay " + pendientes + " comprobantes electrónicos sin CAE.",
                            "form-warning", "Ver pendientes", AppView.FACTURACION_PENDIENTES));
                }
            } catch (RuntimeException e) {
                // Sin aviso.
            }
        }

        seccionAvisos.getChildren().setAll(avisos);
        mostrar(seccionAvisos, !avisos.isEmpty());
    }

    private HBox aviso(String texto, String estilo, String accion, AppView destino) {

        Label mensaje = new Label(texto);
        mensaje.setWrapText(true);
        mensaje.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(mensaje, Priority.ALWAYS);

        Button boton = new Button(accion);
        boton.getStyleClass().add("btn-secondary");
        boton.setOnAction(e -> navigator.navigate(destino));

        HBox aviso = new HBox(12, mensaje, boton);
        aviso.setAlignment(Pos.CENTER_LEFT);
        aviso.getStyleClass().add(estilo);
        aviso.setMaxWidth(900);
        return aviso;
    }

    private void mostrarVentasPorCaja(List<VentasPorCaja> cajas) {

        mostrarVentas(ResumenVentas.de(cajas));

        tblVentasPorCaja.getItems().setAll(cajas);

        // Encabezado + filas (al menos dos, para que se lea el mensaje de "sin ventas").
        tblVentasPorCaja.setPrefHeight(ALTO_FILA * (Math.max(cajas.size(), 2) + 1) + 4);
    }

    private void mostrarVentas(ResumenVentas resumen) {

        lblVentasCantidad.setText(String.valueOf(resumen.cantidad()));
        lblVentasImporte.setText(CurrencyUtils.format(resumen.total()));
    }

    private void mostrarCaja(CajaDto caja) {

        if (caja == null) {
            lblCajaEstado.setText("Sin caja abierta");
            lblCajaDetalle.setText("Ábrala desde Caja para registrar ventas.");
            marcar(tarjetaCaja, "tarjeta-alerta", true);
            return;
        }

        lblCajaEstado.setText("Abierta · " + CurrencyUtils.format(caja.getSaldoEsperado()));
        lblCajaDetalle.setText("Desde " + DateUtils.format(caja.getFechaApertura()));
        marcar(tarjetaCaja, "tarjeta-alerta", false);
    }

    private void mostrarStock(ResumenStock resumen) {

        lblStockBajo.setText(String.valueOf(resumen.conStockBajo()));
        lblSinStock.setText(String.valueOf(resumen.sinStock()));

        marcar(tarjetaStockBajo, "tarjeta-alerta", resumen.conStockBajo() > 0);
        marcar(tarjetaSinStock, "tarjeta-peligro", resumen.sinStock() > 0);
    }

    private static void columna(TableColumn<VentasPorCaja, String> columna,
                                Function<VentasPorCaja, String> valor) {

        columna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
    }

    private static void marcar(Node nodo, String clase, boolean activa) {

        nodo.getStyleClass().remove(clase);

        if (activa) {
            nodo.getStyleClass().add(clase);
        }
    }

    private static void mostrar(Node nodo, boolean visible) {

        nodo.setVisible(visible);
        nodo.setManaged(visible);
    }

    private static String capitalizar(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
