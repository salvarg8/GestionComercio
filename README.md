# GestionComercio

Sistema de gestión comercial de escritorio (JavaFX + Spring Boot) sobre PostgreSQL.
Varias PC (cajas, depósito) pueden trabajar a la vez sobre la misma base.

## Requisitos

- Java 21
- Maven 3.9+
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (para la base de desarrollo y los tests de integración)

## Base de datos para desarrollo

El archivo [docker-compose.yml](docker-compose.yml) levanta PostgreSQL 17 con la base, el usuario y la contraseña que espera la aplicación:

```bash
docker compose up -d      # levantar (los datos quedan en un volumen)
docker compose ps         # ver el estado
docker compose down       # detener (conserva los datos)
docker compose down -v    # detener y borrar los datos
```

| Dato       | Valor por defecto                                    | Variable de entorno |
|------------|------------------------------------------------------|---------------------|
| URL        | `jdbc:postgresql://localhost:5432/gestion_comercio` | `DB_URL`            |
| Usuario    | `gestion`                                            | `DB_USER`           |
| Contraseña | `gestion`                                            | `DB_PASSWORD`       |

Para conectar otra PC de la red, apuntar `DB_URL` al servidor, por ejemplo
`jdbc:postgresql://192.168.0.10:5432/gestion_comercio`.

Al arrancar contra una base vacía, Hibernate crea las tablas y se cargan los
permisos, la empresa de ejemplo y el usuario `admin` / `admin123`.

## Ejecutar

```bash
mvn javafx:run
```

## Tests

```bash
mvn test
```

Los tests de integración (`*IntegrationTest`) levantan su propio PostgreSQL con
[Testcontainers](https://testcontainers.com/), así que Docker tiene que estar en
ejecución. No usan la base de desarrollo.

## Pasar los datos de la base SQLite anterior (opcional)

Hasta COM40 la aplicación usaba SQLite (`data/gestion-comercio.db`). El script
[scripts/migracion/MigrarSqliteAPostgres.java](scripts/migracion/MigrarSqliteAPostgres.java)
copia esos datos a PostgreSQL:

1. Levantar PostgreSQL y arrancar la aplicación una vez, para que se creen las tablas. Cerrarla.
2. Descargar los drivers al repositorio local de Maven:

   ```bash
   mvn dependency:get -Dartifact=org.xerial:sqlite-jdbc:3.50.3.0
   mvn dependency:get -Dartifact=org.postgresql:postgresql:42.7.7
   ```

3. Ejecutar el script desde la raíz del proyecto.

   Windows (PowerShell):

   ```powershell
   $R = "$env:USERPROFILE\.m2\repository"
   java -cp "$R\org\xerial\sqlite-jdbc\3.50.3.0\sqlite-jdbc-3.50.3.0.jar;$R\org\postgresql\postgresql\42.7.7\postgresql-42.7.7.jar" `
        scripts\migracion\MigrarSqliteAPostgres.java data\gestion-comercio.db
   ```

   Linux/macOS:

   ```bash
   R="$HOME/.m2/repository"
   java -cp "$R/org/xerial/sqlite-jdbc/3.50.3.0/sqlite-jdbc-3.50.3.0.jar:$R/org/postgresql/postgresql/42.7.7/postgresql-42.7.7.jar" \
        scripts/migracion/MigrarSqliteAPostgres.java data/gestion-comercio.db
   ```

   Opcionalmente se pasan URL, usuario y contraseña como argumentos 2, 3 y 4.

El script vacía las tablas de PostgreSQL (incluidos los datos iniciales), copia
cada tabla en una sola transacción y ajusta las secuencias de los id. Si algo
falla, PostgreSQL queda como estaba.
