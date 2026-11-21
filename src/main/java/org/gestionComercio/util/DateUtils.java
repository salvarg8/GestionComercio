package org.gestionComercio.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class DateUtils {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DateUtils() {
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Último instante del día con la precisión de PostgreSQL (microsegundos),
     * para usarlo como límite incluido en un filtro "hasta". Con
     * {@code LocalTime.MAX} (nanosegundos) la base lo redondea a las 00:00 del
     * día siguiente y una operación registrada a esa hora entraría en el período.
     *
     * @return null si el día es null (sin límite)
     */
    public static LocalDateTime finDelDia(LocalDate dia) {
        return dia == null ? null : dia.atTime(LocalTime.MAX).truncatedTo(ChronoUnit.MICROS);
    }

    public static String format(LocalDate date) {

        if (date == null) {
            return "";
        }

        return date.format(DATE_FORMAT);

    }

    public static String format(LocalDateTime dateTime) {

        if (dateTime == null) {
            return "";
        }

        return dateTime.format(DATE_TIME_FORMAT);

    }

}