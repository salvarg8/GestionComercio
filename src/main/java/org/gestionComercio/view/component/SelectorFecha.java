package org.gestionComercio.view.component;

import javafx.scene.control.DatePicker;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Formato común de los {@link DatePicker} de los filtros por fecha.
 */
public final class SelectorFecha {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SelectorFecha() {
    }

    /** Formato dd/MM/yyyy; un texto que no es una fecha válida deja el campo vacío. */
    public static void configurar(DatePicker datePicker) {

        datePicker.setPromptText("dd/mm/aaaa");

        datePicker.setConverter(new StringConverter<>() {

            @Override
            public String toString(LocalDate fecha) {
                return fecha == null ? "" : fecha.format(FORMATO_FECHA);
            }

            @Override
            public LocalDate fromString(String texto) {

                if (texto == null || texto.isBlank()) {
                    return null;
                }

                try {
                    return LocalDate.parse(texto.trim(), FORMATO_FECHA);
                } catch (DateTimeParseException e) {
                    return null;
                }
            }
        });
    }
}
