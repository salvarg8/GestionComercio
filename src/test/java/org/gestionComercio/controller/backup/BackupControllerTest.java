package org.gestionComercio.controller.backup;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.navigation.AppView;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.service.BackupService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Carga la vista de copias de seguridad con su controlador y verifica que
 * cada sección se muestre según los permisos.
 */
class BackupControllerTest {

    @BeforeAll
    static void iniciarJavaFx() {
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ex) {
            // Ya iniciado por otro test.
        }
    }

    @Test
    @DisplayName("con ambos permisos se ven crear y restaurar")
    void ambosPermisos() throws Exception {
        Map<String, Object> vista = cargar(EnumSet.of(PermisoCodigo.BACKUP_CREAR, PermisoCodigo.BACKUP_RESTAURAR));

        assertThat(visible(vista, "panelCrear")).isTrue();
        assertThat(visible(vista, "panelRestaurar")).isTrue();
    }

    @Test
    @DisplayName("sólo con BACKUP_CREAR no se ve la restauración")
    void soloCrear() throws Exception {
        Map<String, Object> vista = cargar(EnumSet.of(PermisoCodigo.BACKUP_CREAR));

        assertThat(visible(vista, "panelCrear")).isTrue();
        assertThat(visible(vista, "panelRestaurar")).isFalse();
        assertThat(visible(vista, "btnUsarSeleccionada")).isFalse();
    }

    @Test
    @DisplayName("sólo con BACKUP_RESTAURAR no se ve la creación")
    void soloRestaurar() throws Exception {
        Map<String, Object> vista = cargar(EnumSet.of(PermisoCodigo.BACKUP_RESTAURAR));

        assertThat(visible(vista, "panelCrear")).isFalse();
        assertThat(visible(vista, "panelRestaurar")).isTrue();
    }

    private static Map<String, Object> cargar(EnumSet<PermisoCodigo> permisos) throws Exception {

        SesionUsuario sesion = new SesionUsuario();
        sesion.iniciarSesion(Usuario.builder().usuario("u").build(), permisos);

        BackupService servicio = mock(BackupService.class);
        when(servicio.getCarpetaAutomatica()).thenReturn(Path.of("data", "backups").toAbsolutePath());
        when(servicio.getRetencion()).thenReturn(14);
        when(servicio.listarBackupsAutomaticos()).thenReturn(List.of());

        CompletableFuture<Map<String, Object>> vista = new CompletableFuture<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(BackupControllerTest.class.getResource(AppView.BACKUP.getFxml()));
                loader.setControllerFactory(tipo -> new BackupController(servicio, sesion));
                loader.load();
                vista.complete(loader.getNamespace());
            } catch (Exception ex) {
                vista.completeExceptionally(ex);
            }
        });

        return vista.get(30, TimeUnit.SECONDS);
    }

    private static boolean visible(Map<String, Object> vista, String id) {
        Node nodo = (Node) vista.get(id);
        assertThat(nodo).as(id).isNotNull();
        return nodo.isVisible() && nodo.isManaged();
    }
}
