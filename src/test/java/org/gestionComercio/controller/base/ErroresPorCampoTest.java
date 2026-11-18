package org.gestionComercio.controller.base;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.gestionComercio.validation.ValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Usa controles sueltos (sin escena ni ventana): alcanza con iniciar el toolkit
 * de JavaFX, sin abrir nada. No se prueba {@link ErroresPorCampo#mostrar(ValidationResult)}
 * con errores sin campo, porque abre una ventana.
 */
class ErroresPorCampoTest {

    private static boolean toolkitDisponible;

    @BeforeAll
    static void iniciarToolkit() {
        try {
            Platform.startup(() -> { });
            toolkitDisponible = true;
        } catch (IllegalStateException yaIniciado) {
            toolkitDisponible = true;
        } catch (RuntimeException | UnsatisfiedLinkError sinPantalla) {
            // Por ejemplo, en un servidor de CI sin entorno gráfico.
            toolkitDisponible = false;
        }
    }

    private TextField txtNombre;
    private Label lblErrorNombre;
    private TextField txtEmail;
    private Label lblErrorEmail;

    private ErroresPorCampo errores;

    @BeforeEach
    void setUp() {
        assumeTrue(toolkitDisponible, "JavaFX no disponible en este entorno");

        txtNombre = new TextField();
        lblErrorNombre = new Label();
        txtEmail = new TextField();
        lblErrorEmail = new Label();

        errores = new ErroresPorCampo()
                .registrar("nombre", txtNombre, lblErrorNombre, txtNombre.textProperty())
                .registrar("email", txtEmail, lblErrorEmail);

        errores.limpiar();
    }

    private static ValidationResult resultado(String... campoYMensaje) {
        ValidationResult result = new ValidationResult();
        for (int i = 0; i < campoYMensaje.length; i += 2) {
            result.addError(campoYMensaje[i], campoYMensaje[i + 1]);
        }
        return result;
    }

    @Test
    @DisplayName("marcar() muestra el mensaje debajo del campo y lo marca como inválido")
    void marcaElCampo() {
        boolean todosUbicados = errores.marcar(resultado("nombre", "El nombre es obligatorio."));

        assertThat(todosUbicados).isTrue();
        assertThat(lblErrorNombre.getText()).isEqualTo("El nombre es obligatorio.");
        assertThat(lblErrorNombre.isVisible()).isTrue();
        assertThat(lblErrorNombre.isManaged()).isTrue();
        assertThat(txtNombre.getStyleClass()).contains(ErroresPorCampo.CLASE_INVALIDO);
        assertThat(errores.tieneError("nombre")).isTrue();
        assertThat(errores.tieneError("email")).isFalse();
    }

    @Test
    @DisplayName("marcar() avisa si algún error no corresponde a un campo del formulario")
    void errorSinCampo() {
        boolean todosUbicados = errores.marcar(resultado(
                "nombre", "El nombre es obligatorio.",
                "general", "Error que no es de un campo."));

        assertThat(todosUbicados).isFalse();
        assertThat(errores.tieneError("nombre")).isTrue();
    }

    @Test
    @DisplayName("si un campo tiene varios errores, se muestran todos")
    void variosErroresEnUnCampo() {
        errores.marcar(resultado(
                "email", "El email no tiene un formato válido.",
                "email", "El email no puede superar los 100 caracteres."));

        assertThat(lblErrorEmail.getText())
                .isEqualTo("El email no tiene un formato válido.\nEl email no puede superar los 100 caracteres.");
    }

    @Test
    @DisplayName("la clase de error se agrega una sola vez aunque haya varios errores")
    void claseUnaSolaVez() {
        errores.mostrar("nombre", "Uno.");
        errores.mostrar("nombre", "Dos.");

        assertThat(txtNombre.getStyleClass())
                .filteredOn(ErroresPorCampo.CLASE_INVALIDO::equals)
                .hasSize(1);
    }

    @Test
    @DisplayName("el error se borra cuando el usuario cambia el valor del campo")
    void seBorraAlCorregir() {
        errores.mostrar("nombre", "El nombre es obligatorio.");

        txtNombre.setText("Juan");

        assertThat(errores.tieneError("nombre")).isFalse();
        assertThat(lblErrorNombre.getText()).isEmpty();
        assertThat(lblErrorNombre.isManaged()).isFalse();
        assertThat(txtNombre.getStyleClass()).doesNotContain(ErroresPorCampo.CLASE_INVALIDO);
    }

    @Test
    @DisplayName("un campo registrado sin valor a observar conserva el error hasta limpiar()")
    void sinValorObservado() {
        errores.mostrar("email", "El email no tiene un formato válido.");

        txtEmail.setText("otro@mail.com");
        assertThat(errores.tieneError("email")).isTrue();

        errores.limpiar();
        assertThat(errores.tieneError("email")).isFalse();
    }

    @Test
    @DisplayName("limpiar(campo) borra sólo ese campo e ignora campos no registrados")
    void limpiarUnCampo() {
        errores.mostrar("nombre", "Uno.");
        errores.mostrar("email", "Dos.");

        errores.limpiar("nombre");
        errores.limpiar("noExiste");

        assertThat(errores.tieneError("nombre")).isFalse();
        assertThat(errores.tieneError("email")).isTrue();
    }

    @Test
    @DisplayName("mostrar() en un campo no registrado es un error de programación")
    void campoNoRegistrado() {
        assertThatThrownBy(() -> errores.mostrar("noExiste", "Mensaje."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("noExiste");
    }
}
