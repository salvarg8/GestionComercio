package org.gestionComercio.controller.caja;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.gestionComercio.dto.caja.CajaDto;
import org.gestionComercio.exception.BusinessException;
import org.gestionComercio.service.CajaService;
import org.gestionComercio.util.DateUtils;
import org.gestionComercio.util.dialog.Dialogs;

import java.util.List;
import java.util.function.Function;

/**
 * Cajas abiertas de los demás usuarios. Permite cerrar con arqueo la caja
 * seleccionada ({@link CierreCajaDialog}), por ejemplo cuando el cajero no
 * está al terminar el turno. Requiere CAJA_CERRAR_OTRO_USUARIO.
 */
class CajasAbiertasDialog {

    private final CajaService cajaService;

    private final TableView<CajaDto> tabla = new TableView<>();
    private final Button btnCerrar = new Button("Cerrar caja");
    private final Label lblTotal = new Label();

    CajasAbiertasDialog(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    void mostrar() {

        configurarTabla();

        btnCerrar.getStyleClass().add("btn-danger");
        btnCerrar.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());
        btnCerrar.setOnAction(e -> cerrarSeleccionada());

        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox pie = new HBox(10, lblTotal, espacio, btnCerrar);
        pie.setAlignment(Pos.CENTER_LEFT);

        VBox cuerpo = new VBox(12, tabla, pie);

        cargar();

        Dialogs.crear(Dialogs.Tipo.INFORMACION, "Cajas abiertas de otros usuarios", cuerpo, Dialogs.ACEPTAR)
                .showAndWait();
    }

    private void configurarTabla() {

        tabla.getStyleClass().add("tabla-centrada");
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tabla.setPrefSize(760, 320);
        tabla.setPlaceholder(new Label("No hay cajas abiertas de otros usuarios."));

        tabla.getColumns().setAll(List.of(
                columna("Responsable", 200,
                        c -> CajaFormato.usuario(c.getUsuarioNombreCompleto(), c.getUsuarioNombre())),
                columna("Apertura", 130, c -> DateUtils.format(c.getFechaApertura())),
                columna("Abierta por", 200,
                        c -> CajaFormato.usuario(c.getUsuarioAperturaNombreCompleto(), c.getUsuarioAperturaNombre())),
                columna("Saldo actual", 130, c -> CajaFormato.importe(c.getSaldoEsperado()))));
    }

    private static TableColumn<CajaDto, String> columna(String titulo, double ancho, Function<CajaDto, String> valor) {
        TableColumn<CajaDto, String> columna = new TableColumn<>(titulo);
        columna.setPrefWidth(ancho);
        columna.setSortable(false);
        columna.setCellValueFactory(c -> new SimpleStringProperty(valor.apply(c.getValue())));
        return columna;
    }

    private void cargar() {

        try {

            List<CajaDto> cajas = cajaService.listarCajasAbiertasDeOtrosUsuarios();

            tabla.getItems().setAll(cajas);
            lblTotal.setText("Total: " + cajas.size() + (cajas.size() == 1 ? " caja abierta" : " cajas abiertas"));

        } catch (BusinessException ex) {

            Dialogs.error("Cajas abiertas", ex.getMessage());

        } catch (Exception ex) {

            Dialogs.error("Cajas abiertas",
                    "No se pudieron consultar las cajas abiertas. Si el problema persiste, comuníquese con soporte.");
        }
    }

    private void cerrarSeleccionada() {

        CajaDto seleccionada = tabla.getSelectionModel().getSelectedItem();

        if (seleccionada == null) {
            return;
        }

        // Se recarga antes para arquear contra el saldo más reciente.
        cargar();

        CajaDto caja = tabla.getItems().stream()
                .filter(c -> c.getId().equals(seleccionada.getId()))
                .findFirst()
                .orElse(null);

        if (caja == null) {
            Dialogs.advertencia("Cerrar caja", "La caja seleccionada ya no está abierta.");
            return;
        }

        tabla.getSelectionModel().select(caja);

        new CierreCajaDialog(cajaService, caja).mostrar().ifPresent(c -> {

            cargar();

            Dialogs.exito("Cerrar caja", "Se cerró la caja de "
                    + CajaFormato.usuario(c.getUsuarioNombreCompleto(), c.getUsuarioNombre()) + ".\n\n"
                    + "Saldo esperado: " + CajaFormato.importe(c.getSaldoEsperado()) + "\n"
                    + "Saldo contado: " + CajaFormato.importe(c.getSaldoFinal()) + "\n"
                    + "Diferencia: " + CajaFormato.textoDiferencia(c.getDiferencia()));
        });
    }
}
