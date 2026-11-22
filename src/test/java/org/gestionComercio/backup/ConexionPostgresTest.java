package org.gestionComercio.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConexionPostgresTest {

    @Test
    @DisplayName("toma servidor, puerto y base de la URL JDBC")
    void urlCompleta() {
        ConexionPostgres conexion = ConexionPostgres.desdeJdbc(
                "jdbc:postgresql://192.168.0.10:5433/gestion_comercio", "gestion", "secreta");

        assertThat(conexion.host()).isEqualTo("192.168.0.10");
        assertThat(conexion.puerto()).isEqualTo(5433);
        assertThat(conexion.base()).isEqualTo("gestion_comercio");
    }

    @Test
    @DisplayName("sin puerto usa el 5432 e ignora los parámetros JDBC")
    void puertoPorDefectoYParametros() {
        ConexionPostgres conexion = ConexionPostgres.desdeJdbc(
                "jdbc:postgresql://servidor/gestion_comercio?ApplicationName=caja1&ssl=false", "u", "p");

        assertThat(conexion.host()).isEqualTo("servidor");
        assertThat(conexion.puerto()).isEqualTo(5432);
        assertThat(conexion.base()).isEqualTo("gestion_comercio");
    }

    @Test
    @DisplayName("la conexión va por variables de entorno y la contraseña no aparece en toString")
    void entorno() {
        ConexionPostgres conexion = new ConexionPostgres("localhost", 5432, "base", "gestion", "secreta");

        assertThat(conexion.entorno()).containsAllEntriesOf(Map.of(
                "PGHOST", "localhost",
                "PGPORT", "5432",
                "PGUSER", "gestion",
                "PGPASSWORD", "secreta"));
        assertThat(conexion.toString()).doesNotContain("secreta");
    }

    @Test
    @DisplayName("rechaza URLs que no son de PostgreSQL o no indican la base")
    void urlInvalida() {
        assertThatThrownBy(() -> ConexionPostgres.desdeJdbc("jdbc:sqlite:data/base.db", "u", "p"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ConexionPostgres.desdeJdbc("jdbc:postgresql://servidor/", "u", "p"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("el resumen de un comando fallido muestra los últimos renglones")
    void resumenDeComando() {
        ResultadoComando resultado = new ResultadoComando(1, "a\nb\n\nc\nd\ne\nf\n");

        assertThat(resultado.exitoso()).isFalse();
        assertThat(resultado.resumen()).isEqualTo("b\nc\nd\ne\nf");
        assertThat(new ResultadoComando(2, "").resumen()).isEqualTo("código de salida 2");
    }
}
