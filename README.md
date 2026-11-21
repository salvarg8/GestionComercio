# GestionComercio

Sistema de gestión comercial de escritorio (JavaFX + Spring Boot) sobre PostgreSQL.
Varias PC (cajas, depósito) pueden trabajar a la vez sobre la misma base.

## Requisitos

- Java 21
- Maven 3.9+
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (para la base de desarrollo y los tests de integración)

## Inicio rápido

Desde cero, con Docker en ejecución:

```bash
git clone https://github.com/salvarg8/GestionComercio.git
cd GestionComercio
docker compose up -d      # PostgreSQL de desarrollo
mvn javafx:run            # compila y abre la aplicación
```

En el primer arranque se crean las tablas y los datos iniciales. Ingresar con
el usuario **`admin`** y la contraseña **`admin123`**, y cambiarla desde
el perfil (Cambiar contraseña).

## Compilar

```bash
mvn clean package         # compila, corre Checkstyle y los tests
mvn clean package -DskipTests
```

Checkstyle ([checkstyle.xml](checkstyle.xml)) corre en la fase `validate`: una
violación corta la compilación.

## Estructura

| Carpeta                              | Contenido                                              |
|--------------------------------------|--------------------------------------------------------|
| `src/main/java/org/gestionComercio`  | Código: `entity`, `repository`, `service`, `controller`, `view`, etc. |
| `src/main/resources`                 | `application.properties`, vistas FXML, estilos y migraciones Flyway |
| `src/test`                           | Tests unitarios y de integración                       |
| `scripts/migracion`                  | Script para pasar datos de SQLite a PostgreSQL         |
| `data/` (no versionada)              | Datos locales: logos subidos, base SQLite anterior     |

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

Al arrancar contra una base vacía, Flyway crea las tablas y `DataInitializer`
carga los permisos, la empresa de ejemplo y el usuario `admin` / `admin123`.

## Migraciones de base de datos (Flyway)

El esquema lo definen sólo los scripts de
[src/main/resources/db/migration](src/main/resources/db/migration), que Flyway
aplica al arrancar la aplicación. Hibernate no crea ni modifica tablas
(`ddl-auto=validate`): si las entidades no coinciden con el esquema, la
aplicación no arranca.

**Regla del equipo:** cada cambio de entidad (tabla, columna, tipo, índice,
restricción) viene en el mismo PR con su script
`V<n>__descripcion.sql`, con `<n>` el siguiente número libre. Un script ya
mergeado no se modifica nunca: un error se corrige con otro script.

- Cada migración corre en una transacción y PostgreSQL tiene DDL transaccional:
  si falla, se revierte entera y la aplicación no arranca.
- Los datos iniciales (permisos, rol y usuario administrador, empresa) los
  mantiene `DataInitializer`, no las migraciones: los permisos salen del enum
  `PermisoCodigo`, así que agregar uno no requiere script.
- Las columnas de enums son `varchar` sin `CHECK` de valores, así que agregar un
  valor a un enum tampoco requiere script (renombrarlo o quitarlo, sí).
- Los tests de integración usan las mismas migraciones.

### Base de desarrollo creada antes de COM31

Si la base del `docker-compose.yml` la creó Hibernate (antes de Flyway), la
aplicación no arranca: Flyway no aplica migraciones sobre un esquema que no
creó. Hay que recrearla:

```bash
docker compose down -v
docker compose up -d
```

y arrancar la aplicación una vez. Para recuperar los datos de la base SQLite
anterior, seguir después la sección siguiente.

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

1. Levantar PostgreSQL y arrancar la aplicación una vez, para que Flyway cree las tablas. Cerrarla.
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

El script vacía las tablas de PostgreSQL (incluidos los datos iniciales, pero no
el historial de Flyway), copia
cada tabla en una sola transacción y ajusta las secuencias de los id. Si algo
falla, PostgreSQL queda como estaba.
