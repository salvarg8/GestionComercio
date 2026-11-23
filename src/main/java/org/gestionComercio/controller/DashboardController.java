package org.gestionComercio.controller;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import org.gestionComercio.controller.base.AbstractController;
import org.gestionComercio.controller.licencia.CambiosLicencia;
import org.gestionComercio.licencia.EstadoLicencia;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.navigation.Navigator;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.LicenciaService;
import org.gestionComercio.service.LogoutService;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Scope("prototype")
public class DashboardController extends AbstractController {

    private final Navigator navigator;
    private final SesionUsuario sesionUsuario;
    private final LogoutService logoutService;
    private final LicenciaService licenciaService;
    private final CambiosLicencia cambiosLicencia;

    /** Referencia fuerte: se registra como WeakChangeListener para no retener el dashboard de sesiones anteriores. */
    private final ChangeListener<EstadoLicencia> alCambiarLicencia = (obs, anterior, estado) -> {
        if (estado != null) {
            mostrarEstadoLicencia(estado);
        }
    };

    @FXML
    private StackPane contentPane;

    @FXML
    private Label lblUsuario;

    @FXML
    private Button btnInicio;

    @FXML
    private Button btnRoles;

    @FXML
    private Button btnPermisos;

    @FXML
    private Button btnUsuarios;

    @FXML
    private Button btnProductos;

    @FXML
    private Button btnStock;

    @FXML
    private Button btnMovimientos;

    @FXML
    private Button btnCategorias;

    @FXML
    private Button btnMarcas;

    @FXML
    private Button btnProveedores;

    @FXML
    private Button btnClientes;

    @FXML
    private Button btnCaja;

    @FXML
    private Button btnVentas;

    @FXML
    private Button btnCompras;

    @FXML
    private Button btnReportes;

    @FXML
    private Button btnConfiguracion;

    @FXML
    private Button btnFacturacion;

    @FXML
    private Button btnBackup;

    @FXML
    private Button btnLicencia;

    @FXML
    private Label lblLicencia;

    @FXML
    private Button btnCambiarPassword;

    @FXML
    private Button btnCerrarSesion;

    @Override
    protected void initializeComponents() {

        navigator.setContentPane(contentPane);

        lblUsuario.setText(
                sesionUsuario.getUsuario().getNombre()
        );

        mostrarSiTieneAcceso(btnRoles, AppView.ROL_LIST);
        mostrarSiTieneAcceso(btnPermisos, AppView.PERMISO_LIST);
        mostrarSiTieneAcceso(btnUsuarios, AppView.USUARIO_LIST);
        mostrarSiTieneAcceso(btnProductos, AppView.PRODUCTO_LIST);
        mostrarSiTieneAcceso(btnStock, AppView.STOCK_CONSULTA);
        mostrarSiTieneAcceso(btnMovimientos, AppView.MOVIMIENTO_HISTORIAL);
        mostrarSiTieneAcceso(btnCategorias, AppView.CATEGORIA_LIST);
        mostrarSiTieneAcceso(btnMarcas, AppView.MARCA_LIST);
        mostrarSiTieneAcceso(btnProveedores, AppView.PROVEEDOR_LIST);
        mostrarSiTieneAcceso(btnClientes, AppView.CLIENTE_LIST);
        mostrarSiTieneAcceso(btnCaja, AppView.CAJA);
        mostrarSiTieneAcceso(btnVentas, vistaDeVentas());
        mostrarSiTieneAcceso(btnCompras, vistaDeCompras());
        mostrarSiTieneAcceso(btnReportes, AppView.REPORTES);
        mostrarSiTieneAcceso(btnConfiguracion, AppView.EMPRESA_CONFIG);
        mostrarSiTieneAcceso(btnFacturacion, vistaDeFacturacion());
        mostrarSiTieneAcceso(btnBackup, AppView.BACKUP);
        mostrarSiTieneAcceso(btnLicencia, AppView.LICENCIA);

        try {
            mostrarEstadoLicencia(licenciaService.estado());
        } catch (RuntimeException e) {
            // Sin indicador.
        }
        cambiosLicencia.estadoProperty().addListener(new WeakChangeListener<>(alCambiarLicencia));

        navigator.navigate(AppView.INICIO);
    }

    @Override
    protected void initializeEvents() {

        btnInicio.setOnAction(e -> navigator.navigate(AppView.INICIO));

        btnRoles.setOnAction(e ->
                navigator.navigate(AppView.ROL_LIST));

        btnPermisos.setOnAction(e -> navigator.navigate(AppView.PERMISO_LIST));

        btnUsuarios.setOnAction(e -> navigator.navigate(AppView.USUARIO_LIST));

        btnProductos.setOnAction(e -> navigator.navigate(AppView.PRODUCTO_LIST));

        btnStock.setOnAction(e -> navigator.navigate(AppView.STOCK_CONSULTA));

        btnMovimientos.setOnAction(e -> navigator.navigate(AppView.MOVIMIENTO_HISTORIAL));

        btnCategorias.setOnAction(e -> navigator.navigate(AppView.CATEGORIA_LIST));

        btnMarcas.setOnAction(e -> navigator.navigate(AppView.MARCA_LIST));

        btnProveedores.setOnAction(e -> navigator.navigate(AppView.PROVEEDOR_LIST));

        btnClientes.setOnAction(e -> navigator.navigate(AppView.CLIENTE_LIST));

        btnCaja.setOnAction(e -> navigator.navigate(AppView.CAJA));

        btnVentas.setOnAction(e -> navigator.navigate(vistaDeVentas()));

        btnCompras.setOnAction(e -> navigator.navigate(vistaDeCompras()));

        btnReportes.setOnAction(e -> navigator.navigate(AppView.REPORTES));

        btnConfiguracion.setOnAction(e -> navigator.navigate(AppView.EMPRESA_CONFIG));

        btnFacturacion.setOnAction(e -> navigator.navigate(vistaDeFacturacion()));

        btnBackup.setOnAction(e -> navigator.navigate(AppView.BACKUP));

        btnLicencia.setOnAction(e -> navigator.navigate(AppView.LICENCIA));

        btnCambiarPassword.setOnAction(e -> navigator.navigate(AppView.CAMBIAR_PASSWORD));

        btnCerrarSesion.setOnAction(e -> cerrarSesion());
    }

    /** "Ventas" abre el punto de venta; quien sólo puede ver ventas va al listado. */
    private AppView vistaDeVentas() {
        return navigator.puedeAcceder(AppView.PUNTO_VENTA) ? AppView.PUNTO_VENTA : AppView.VENTA_LIST;
    }

    /** "Facturación electrónica" abre la configuración; quien sólo puede reintentar va a los pendientes de CAE. */
    private AppView vistaDeFacturacion() {
        return navigator.puedeAcceder(AppView.FACTURACION_CONFIG)
                ? AppView.FACTURACION_CONFIG
                : AppView.FACTURACION_PENDIENTES;
    }

    /** "Compras" abre el listado; quien sólo puede registrar compras va al formulario. */
    private AppView vistaDeCompras() {
        return navigator.puedeAcceder(AppView.COMPRA_LIST) ? AppView.COMPRA_LIST : AppView.COMPRA_FORM;
    }

    /**
     * Indicador en el encabezado: período de prueba, licencia por vencer o
     * modo sólo consulta. Con una licencia activa sin novedades no se muestra.
     * Se actualiza al activar una licencia.
     */
    private void mostrarEstadoLicencia(EstadoLicencia estado) {

        boolean visible = estado.requiereAviso();
        lblLicencia.setVisible(visible);
        lblLicencia.setManaged(visible);

        if (!visible) {
            return;
        }

        String texto = switch (estado.situacion()) {
            case PRUEBA -> "Período de prueba: " + dias(estado.diasRestantes());
            case POR_VENCER -> estado.diasRestantes() == 0
                    ? "La licencia vence hoy"
                    : "La licencia vence en " + dias(estado.diasRestantes());
            default -> "Modo sólo consulta: " + estado.situacion().getDescripcion().toLowerCase();
        };

        lblLicencia.setText(texto);
        lblLicencia.getStyleClass().setAll(estado.permiteOperar() ? "form-warning" : "form-error");
    }

    private static String dias(long dias) {
        return dias + (dias == 1 ? " día" : " días");
    }

    private void mostrarSiTieneAcceso(Button boton, AppView view) {
        boolean visible = navigator.puedeAcceder(view);
        boton.setVisible(visible);
        boton.setManaged(visible);
    }

    private void cerrarSesion() {

        logoutService.logout();

        // El contenedor pertenece a este Dashboard; el próximo login crea uno nuevo.
        navigator.setContentPane(null);

        navigator.navigate(AppView.LOGIN);
    }
}