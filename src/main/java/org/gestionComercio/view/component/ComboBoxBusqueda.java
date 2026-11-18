package org.gestionComercio.view.component;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.util.StringConverter;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * Convierte un {@link ComboBox} en un combo con búsqueda: el usuario escribe y
 * la lista se filtra a los elementos que contienen el texto, sin distinguir
 * mayúsculas ni acentos.
 *
 * <p>El valor del combo sólo cambia cuando se elige un elemento. Al confirmar
 * (Enter o salir del campo) con un texto parcial se elige la primera
 * coincidencia; si no hay ninguna, o con Escape, se vuelve a mostrar el valor
 * que tenía. Con el texto vacío se elige el primer elemento "fijo" (por
 * ejemplo "Todos"), si lo hay.
 *
 * <p>Uso:
 * <pre>{@code
 * ObservableList<T> elementos = ComboBoxBusqueda.configurar(combo, T::nombre, t -> t.codigo() + " " + t.nombre());
 * elementos.setAll(lista);
 * }</pre>
 *
 * @param <T> tipo de los elementos
 */
public final class ComboBoxBusqueda<T> {

    private final ComboBox<T> combo;
    private final Function<T, String> textoMostrado;
    private final Function<T, String> textoBusqueda;
    private final ObservableList<T> elementos = FXCollections.observableArrayList();
    private final FilteredList<T> filtrados = new FilteredList<>(elementos);

    private ComboBoxBusqueda(ComboBox<T> combo, Function<T, String> textoMostrado,
                             Function<T, String> textoBusqueda) {
        this.combo = combo;
        this.textoMostrado = textoMostrado;
        this.textoBusqueda = textoBusqueda;
    }

    /**
     * Configura el combo y devuelve la lista de elementos a cargar. Hay que
     * modificar esa lista y no {@code combo.getItems()}, que es la vista filtrada.
     *
     * @param textoMostrado texto de cada elemento en el combo y en la lista
     * @param textoBusqueda texto en el que se busca; si devuelve null el
     *                      elemento es fijo: se muestra siempre, sin importar lo escrito
     */
    public static <T> ObservableList<T> configurar(ComboBox<T> combo,
                                                   Function<T, String> textoMostrado,
                                                   Function<T, String> textoBusqueda) {

        ComboBoxBusqueda<T> busqueda = new ComboBoxBusqueda<>(combo, textoMostrado, textoBusqueda);
        busqueda.instalar();
        return busqueda.elementos;
    }

    /**
     * @return true si {@code texto} contiene todas las palabras de {@code busqueda},
     *         en cualquier orden, sin distinguir mayúsculas ni acentos
     */
    public static boolean coincide(String texto, String busqueda) {

        String normalizado = normalizar(texto);

        for (String palabra : normalizar(busqueda).split("\\s+")) {
            if (!palabra.isEmpty() && !normalizado.contains(palabra)) {
                return false;
            }
        }

        return true;
    }

    private static String normalizar(String texto) {

        if (texto == null) {
            return "";
        }

        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private void instalar() {

        combo.setItems(filtrados);
        combo.setEditable(true);
        combo.setPlaceholder(new Label("Sin coincidencias"));

        combo.setConverter(new StringConverter<>() {

            @Override
            public String toString(T elemento) {
                return elemento == null ? "" : textoMostrado.apply(elemento);
            }

            @Override
            public T fromString(String texto) {
                return elegir(texto);
            }
        });

        combo.getEditor().textProperty().addListener((obs, anterior, texto) -> filtrar(texto));

        // Al salir del campo, el texto vuelve a reflejar el valor elegido.
        combo.getEditor().focusedProperty().addListener((obs, antes, enfocado) -> {
            if (!enfocado) {
                Platform.runLater(this::mostrarValor);
            }
        });

        combo.getEditor().setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                combo.hide();
                mostrarValor();
            } else if (e.getCode() == KeyCode.DOWN && !combo.isShowing()) {
                combo.show();
            }
        });

        // Al hacer foco se selecciona el texto, para escribir encima directamente.
        combo.getEditor().focusedProperty().addListener((obs, antes, enfocado) -> {
            if (enfocado) {
                Platform.runLater(combo.getEditor()::selectAll);
            }
        });
    }

    /** Elemento que corresponde al texto confirmado (ver la documentación de la clase). */
    private T elegir(String texto) {

        if (texto == null || texto.isBlank()) {
            return elementos.stream().filter(this::esFijo).findFirst().orElse(combo.getValue());
        }

        for (T elemento : elementos) {
            if (texto.equals(textoMostrado.apply(elemento))) {
                return elemento;
            }
        }

        return elementos.stream()
                .filter(e -> !esFijo(e) && coincide(textoBusqueda.apply(e), texto))
                .findFirst()
                .orElse(combo.getValue());
    }

    private void filtrar(String texto) {

        // El texto del valor elegido (o vacío) muestra la lista completa.
        boolean sinFiltro = texto == null || texto.isBlank()
                || Objects.equals(texto, combo.getConverter().toString(combo.getValue()));

        // Diferido: cambiar la lista mientras el combo procesa un evento lo desordena.
        Platform.runLater(() -> {

            filtrados.setPredicate(sinFiltro ? null : e -> esFijo(e) || coincide(textoBusqueda.apply(e), texto));

            if (!sinFiltro && combo.getEditor().isFocused() && !combo.isShowing()) {
                combo.show();
            }
        });
    }

    private void mostrarValor() {
        combo.getEditor().setText(combo.getConverter().toString(combo.getValue()));
        filtrados.setPredicate(null);
    }

    private boolean esFijo(T elemento) {
        return textoBusqueda.apply(elemento) == null;
    }
}
